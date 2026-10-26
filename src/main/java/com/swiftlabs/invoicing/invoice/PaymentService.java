package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payments received on invoices. The balance of an invoice is its net to pay (TTC plus droit de
 * timbre), minus its issued credit notes, minus its payments; it becomes paid when the balance
 * reaches zero.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    /** What an invoice page shows about payment. */
    public record Summary(BigDecimal due, BigDecimal credited, BigDecimal paid, BigDecimal balance) {

        public boolean isPartiallyPaid() {
            return paid.signum() > 0 && balance.signum() > 0;
        }
    }

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final InvoiceAudit audit;
    private final Clock clock;

    public PaymentService(InvoiceRepository invoiceRepository, InvoicePaymentRepository paymentRepository,
                          InvoiceAudit audit, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.audit = audit;
        this.clock = clock;
    }

    public Summary summary(Invoice invoice) {
        BigDecimal credited = invoiceRepository.sumIssuedCreditNotes(invoice.getId());
        BigDecimal paid = paymentRepository.sumByInvoice(invoice.getId());
        return new Summary(invoice.getAmountDue(), credited, paid, invoice.getAmountDue().subtract(credited).subtract(paid));
    }

    /** Summaries of many invoices with two queries (exports); credit notes, quotes and drafts are left out. */
    public Map<Long, Summary> summaries(List<Invoice> documents) {
        List<Long> ids = documents.stream()
            .filter(document -> document.getKind() == DocumentKind.INVOICE && document.isIssuedOrPaid())
            .map(Invoice::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> paid = new HashMap<>();
        paymentRepository.sumByInvoices(ids).forEach(row -> paid.put((Long) row[0], (BigDecimal) row[1]));
        Map<Long, BigDecimal> credited = new HashMap<>();
        invoiceRepository.sumIssuedCreditNotesByInvoices(ids).forEach(row -> credited.put((Long) row[0], (BigDecimal) row[1]));
        Map<Long, Summary> summaries = new HashMap<>();
        for (Invoice invoice : documents) {
            if (ids.contains(invoice.getId())) {
                BigDecimal c = credited.getOrDefault(invoice.getId(), BigDecimal.ZERO);
                BigDecimal p = paid.getOrDefault(invoice.getId(), BigDecimal.ZERO);
                summaries.put(invoice.getId(), new Summary(invoice.getAmountDue(), c, p, invoice.getAmountDue().subtract(c).subtract(p)));
            }
        }
        return summaries;
    }

    public BigDecimal balance(Invoice invoice) {
        return summary(invoice).balance();
    }

    public List<InvoicePayment> paymentsOf(Long invoiceId) {
        return paymentRepository.findByInvoiceIdOrderByPaidOnAscIdAsc(invoiceId);
    }

    /** Records a payment on an issued invoice; the invoice becomes paid once nothing is left to pay. */
    @Transactional
    public InvoicePayment record(Long invoiceId, PaymentForm form) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("invoice.error.notFound", String.valueOf(invoiceId)));
        if (invoice.getKind() != DocumentKind.INVOICE || invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessException("invoice.payment.error.notPayable");
        }
        BigDecimal balance = balance(invoice);
        if (balance.signum() <= 0) {
            throw new BusinessException("invoice.payment.error.nothingLeft");
        }
        if (form.getAmount() == null || form.getAmount().signum() <= 0) {
            throw BusinessException.onField("amount", "invoice.payment.error.amountPositive");
        }
        if (form.getAmount().compareTo(balance) > 0) {
            throw BusinessException.onField("amount", "invoice.payment.error.amountTooHigh", AmountUtils.format(balance));
        }
        LocalDate today = LocalDate.now(clock);
        if (form.getPaidOn() == null || form.getPaidOn().isAfter(today)) {
            throw BusinessException.onField("paidOn", "invoice.payment.error.future");
        }
        if (form.getPaidOn().isBefore(invoice.getIssueDate())) {
            throw BusinessException.onField("paidOn", "invoice.payment.error.beforeInvoice");
        }
        InvoicePayment payment = paymentRepository.save(new InvoicePayment(invoice, AmountUtils.round2(form.getAmount()),
            form.getPaidOn(), form.getMethod(), form.getReference(), currentUsername(), LocalDateTime.now(clock)));
        audit.record(invoice, InvoiceEvent.Action.PAYMENT, InvoiceAudit.payment(payment));
        settleIfPaid(invoice);
        return payment;
    }

    /** Removes a payment typed by mistake; a paid invoice goes back to issued. */
    @Transactional
    public Invoice delete(Long invoiceId, Long paymentId) {
        InvoicePayment payment = paymentRepository.findById(paymentId)
            .filter(p -> p.getInvoice().getId().equals(invoiceId))
            .orElseThrow(() -> new ResourceNotFoundException("invoice.payment.error.notFound"));
        Invoice invoice = payment.getInvoice();
        paymentRepository.delete(payment);
        paymentRepository.flush();
        if (invoice.getStatus() == InvoiceStatus.PAID && balance(invoice).signum() > 0) {
            invoice.setStatus(InvoiceStatus.ISSUED);
            invoice.setPaidAt(null);
        }
        audit.record(invoice, InvoiceEvent.Action.PAYMENT_DELETED, InvoiceAudit.payment(payment));
        return invoice;
    }

    /** Marks an issued invoice paid when its payments (and credit notes) cover it. */
    @Transactional
    public void settleIfPaid(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.ISSUED && paymentRepository.sumByInvoice(invoice.getId()).signum() > 0
                && balance(invoice).signum() <= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(LocalDateTime.now(clock));
            audit.record(invoice, InvoiceEvent.Action.PAID);
        }
    }

    /** Issued invoices with something left to pay. */
    public long countUnpaid() {
        return invoiceRepository.countUnpaid();
    }

    /** What clients still owe on issued invoices: net to pay, minus credit notes and payments. */
    public BigDecimal totalUnpaid() {
        return invoiceRepository.sumUnpaidInvoices()
            .subtract(invoiceRepository.sumCreditNotesOnUnpaidInvoices())
            .subtract(paymentRepository.sumOnUnpaidInvoices());
    }

    /** An overdue invoice and what is left to pay on it. */
    public record Overdue(Invoice invoice, BigDecimal balance, long daysLate) {
    }

    /** Issued invoices past their due date with something left to pay, the oldest due first. */
    public List<Overdue> overdue() {
        LocalDate today = LocalDate.now(clock);
        return invoiceRepository.findAll((root, query, cb) -> InvoiceSpecifications.overdue(root, query, cb), Sort.by("dueDate", "id"))
            .stream()
            .map(invoice -> new Overdue(invoice, balance(invoice), ChronoUnit.DAYS.between(invoice.getDueDate(), today)))
            .toList();
    }

    /** What each client still owes (client id to balance), for clients with issued invoices. */
    public Map<Long, BigDecimal> balancesByClient() {
        Map<Long, BigDecimal> balances = new HashMap<>();
        invoiceRepository.sumUnpaidByClient().forEach(row -> balances.merge((Long) row[0], (BigDecimal) row[1], BigDecimal::add));
        invoiceRepository.sumCreditNotesOnUnpaidByClient()
            .forEach(row -> balances.merge((Long) row[0], ((BigDecimal) row[1]).negate(), BigDecimal::add));
        paymentRepository.sumOnUnpaidByClient()
            .forEach(row -> balances.merge((Long) row[0], ((BigDecimal) row[1]).negate(), BigDecimal::add));
        return balances;
    }

    /** The method in the stored history details and in statements: lower case French ("chèque"). */
    static String methodLabel(PaymentMethod method) {
        return switch (method) {
            case CASH -> "espèces";
            case BANK_TRANSFER -> "virement";
            case CHEQUE -> "chèque";
            case CCP -> "virement CCP";
            case OTHER -> "autre";
        };
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : null;
    }
}
