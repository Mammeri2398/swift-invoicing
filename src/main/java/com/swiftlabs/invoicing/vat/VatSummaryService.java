package com.swiftlabs.invoicing.vat;

import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.invoice.DocumentKind;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoicePayment;
import com.swiftlabs.invoicing.invoice.InvoicePaymentRepository;
import com.swiftlabs.invoicing.invoice.InvoiceRepository;
import com.swiftlabs.invoicing.invoice.InvoiceStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * TVA summaries for the monthly G50 declaration of a company. On invoicing, an issued (or paid) invoice counts in the
 * month of its date and an issued credit note is subtracted; a cancelled invoice does not count. On collection, each
 * payment received carries the invoice's TVA in proportion (amount received / amount due, droit de timbre included).
 */
@Service
@Transactional(readOnly = true)
public class VatSummaryService {

    private static final List<InvoiceStatus> COUNTED = List.of(InvoiceStatus.ISSUED, InvoiceStatus.PAID);

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;

    public VatSummaryService(InvoiceRepository invoiceRepository, InvoicePaymentRepository paymentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    /** What one document or payment adds to its month. */
    private record Part(VatSummary.Entry entry, Map<BigDecimal, BigDecimal[]> rates, BigDecimal exempt) {
    }

    public VatSummary month(int year, int month, VatBasis basis) {
        YearMonth period = YearMonth.of(year, month);
        return summarize(year, month, basis, parts(basis, period.atDay(1), period.atEndOfMonth()));
    }

    /** The twelve months of a year (one query for the year). */
    public List<VatSummary> year(int year, VatBasis basis) {
        List<Part> parts = parts(basis, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        List<VatSummary> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int m = month;
            months.add(summarize(year, month, basis, parts.stream().filter(part -> part.entry().date().getMonthValue() == m).toList()));
        }
        return months;
    }

    private List<Part> parts(VatBasis basis, LocalDate from, LocalDate to) {
        List<Part> parts = new ArrayList<>();
        if (basis == VatBasis.INVOICED) {
            for (Invoice document : invoiceRepository.findForPeriod(DocumentKind.ACCOUNTING, COUNTED, from, to)) {
                parts.add(invoiced(document));
            }
        } else {
            for (InvoicePayment payment : paymentRepository.findReceivedBetween(from, to)) {
                parts.add(collected(payment));
            }
        }
        return parts;
    }

    /** A credit note reduces the month's figures: every amount negative. */
    private static Part invoiced(Invoice document) {
        BigDecimal sign = document.isCreditNote() ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        Map<BigDecimal, BigDecimal[]> rates = new TreeMap<>(Comparator.reverseOrder());
        BigDecimal taxable = BigDecimal.ZERO;
        for (Invoice.VatLine line : document.getVatBreakdown()) {
            rates.put(line.rate(), new BigDecimal[] {line.base().multiply(sign), line.amount().multiply(sign)});
            taxable = taxable.add(line.base());
        }
        BigDecimal exempt = document.getSubtotalAmount().subtract(taxable).multiply(sign);
        BigDecimal stamp = document.getStampDutyAmount().multiply(sign);
        VatSummary.Entry entry = new VatSummary.Entry(document.getIssueDate(), document.getId(), document.getInvoiceNumber(),
            document.getKind(), clientName(document), document.getSubtotalAmount().multiply(sign),
            document.getVatAmount().multiply(sign), stamp, document.getAmountDue().multiply(sign));
        return new Part(entry, rates, exempt);
    }

    /** The share of the invoice this payment settles, applied to each of its amounts. */
    private static Part collected(InvoicePayment payment) {
        Invoice invoice = payment.getInvoice();
        BigDecimal due = invoice.getAmountDue();
        BigDecimal ratio = due.signum() == 0 ? BigDecimal.ZERO : payment.getAmount().divide(due, 10, RoundingMode.HALF_UP);
        Map<BigDecimal, BigDecimal[]> rates = new TreeMap<>(Comparator.reverseOrder());
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (Invoice.VatLine line : invoice.getVatBreakdown()) {
            BigDecimal base = share(line.base(), ratio);
            BigDecimal amount = share(line.amount(), ratio);
            rates.put(line.rate(), new BigDecimal[] {base, amount});
            taxable = taxable.add(base);
            vat = vat.add(amount);
        }
        BigDecimal stamp = share(invoice.getStampDutyAmount(), ratio);
        // What is left of the payment once TVA and droit de timbre are taken out is turnover HT
        BigDecimal exclVat = payment.getAmount().subtract(vat).subtract(stamp);
        VatSummary.Entry entry = new VatSummary.Entry(payment.getPaidOn(), invoice.getId(), invoice.getInvoiceNumber(),
            invoice.getKind(), clientName(invoice), exclVat, vat, stamp, payment.getAmount());
        return new Part(entry, rates, exclVat.subtract(taxable));
    }

    private static VatSummary summarize(int year, int month, VatBasis basis, List<Part> parts) {
        Map<BigDecimal, BigDecimal[]> totals = new TreeMap<>(Comparator.reverseOrder());
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal stamp = BigDecimal.ZERO;
        List<VatSummary.Entry> entries = new ArrayList<>();
        for (Part part : parts) {
            part.rates().forEach((rate, amounts) -> totals.merge(rate, amounts.clone(),
                (a, b) -> new BigDecimal[] {a[0].add(b[0]), a[1].add(b[1])}));
            exempt = exempt.add(part.exempt());
            stamp = stamp.add(part.entry().stampDuty());
            entries.add(part.entry());
        }
        List<VatSummary.Rate> rates = new ArrayList<>();
        totals.forEach((rate, amounts) -> rates.add(new VatSummary.Rate(rate, amounts[0], amounts[1])));
        return new VatSummary(year, month, basis, rates, exempt, stamp, entries);
    }

    private static BigDecimal share(BigDecimal amount, BigDecimal ratio) {
        return AmountUtils.round2(amount.multiply(ratio));
    }

    private static String clientName(Invoice invoice) {
        if (invoice.getClientName() != null) {
            return invoice.getClientName();
        }
        return invoice.getClient() != null ? invoice.getClient().getName() : "";
    }
}
