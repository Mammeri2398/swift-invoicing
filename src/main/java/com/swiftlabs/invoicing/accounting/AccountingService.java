package com.swiftlabs.invoicing.accounting;

import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.invoice.DocumentKind;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoicePayment;
import com.swiftlabs.invoicing.invoice.InvoicePaymentRepository;
import com.swiftlabs.invoicing.invoice.InvoiceRepository;
import com.swiftlabs.invoicing.invoice.InvoiceStatus;
import com.swiftlabs.invoicing.invoice.PaymentMethod;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The accounting entries of a period, as an accountant would book them:
 * <ul>
 *   <li>an invoice: debit the client (amount due), credit sales (HT), TVA collectée per rate and droit de timbre;</li>
 *   <li>a credit note: the same, reversed;</li>
 *   <li>an issued invoice later cancelled: its entry stays on its date, reversed on the day it was cancelled (an
 *       entry already booked by the accountant is never rewritten);</li>
 *   <li>a payment: debit the bank or the cash (cash payments), credit the client.</li>
 * </ul>
 * Labels are in the user's language; amounts and account numbers are the same in every language.
 */
@Service
@Transactional(readOnly = true)
public class AccountingService {

    private static final List<InvoiceStatus> ISSUED = List.of(InvoiceStatus.ISSUED, InvoiceStatus.PAID, InvoiceStatus.CANCELLED);

    private final AccountingSettingsRepository settingsRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final Messages messages;

    public AccountingService(AccountingSettingsRepository settingsRepository, InvoiceRepository invoiceRepository,
                             InvoicePaymentRepository paymentRepository, Messages messages) {
        this.settingsRepository = settingsRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.messages = messages;
    }

    /** The saved accounts, else the SCF defaults. */
    public AccountingSettings settings() {
        return settingsRepository.findById(AccountingSettings.ID).orElseGet(AccountingSettings::defaults);
    }

    @Transactional
    public AccountingSettings save(AccountingForm form) {
        AccountingSettings settings = settings();
        form.applyTo(settings);
        return settingsRepository.save(settings);
    }

    public AccountingJournal journal(LocalDate from, LocalDate to) {
        AccountingSettings accounts = settings();
        List<AccountingJournal.Entry> salesEntries = new ArrayList<>();
        List<AccountingJournal.Sale> sales = new ArrayList<>();
        for (Invoice document : invoiceRepository.findForPeriod(DocumentKind.ACCOUNTING, ISSUED, from, to)) {
            // A cancelled draft never had a number, nor an existence in the accounts
            if (document.getInvoiceNumber() == null) {
                continue;
            }
            boolean creditNote = document.isCreditNote();
            String label = messages.get(creditNote ? MessageProperties.ACCOUNTING_LABEL_CREDIT_NOTE : MessageProperties.ACCOUNTING_LABEL_INVOICE,
                document.getInvoiceNumber(), clientName(document));
            sale(accounts, document, document.getIssueDate(), label, creditNote, salesEntries);
            sales.add(saleRow(document, document.getIssueDate(), false, creditNote));
        }
        for (Invoice invoice : invoiceRepository.findCancelledBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            LocalDate date = invoice.getCancelledAt().toLocalDate();
            String label = messages.get(MessageProperties.ACCOUNTING_LABEL_CANCELLATION, invoice.getInvoiceNumber(), clientName(invoice));
            sale(accounts, invoice, date, label, true, salesEntries);
            sales.add(saleRow(invoice, date, true, true));
        }
        salesEntries.sort(Comparator.comparing(AccountingJournal.Entry::date));
        sales.sort(Comparator.comparing(AccountingJournal.Sale::date));

        List<AccountingJournal.Entry> bankEntries = new ArrayList<>();
        List<AccountingJournal.Entry> cashEntries = new ArrayList<>();
        List<AccountingJournal.Receipt> receipts = new ArrayList<>();
        for (InvoicePayment payment : paymentRepository.findReceivedBetween(from, to)) {
            Invoice invoice = payment.getInvoice();
            boolean cash = payment.getMethod() == PaymentMethod.CASH;
            String journal = cash ? accounts.getCashJournal() : accounts.getBankJournal();
            String label = messages.get(MessageProperties.ACCOUNTING_LABEL_PAYMENT, invoice.getInvoiceNumber(), clientName(invoice));
            List<AccountingJournal.Entry> target = cash ? cashEntries : bankEntries;
            add(target, journal, payment.getPaidOn(), invoice, cash ? accounts.getCashAccount() : accounts.getBankAccount(), label,
                payment.getAmount(), false);
            add(target, journal, payment.getPaidOn(), invoice, accounts.getCustomerAccount(), label, payment.getAmount(), true);
            receipts.add(new AccountingJournal.Receipt(payment.getPaidOn(), invoice.getId(), invoice.getInvoiceNumber(),
                clientName(invoice), payment.getMethod(), payment.getReference(), journal, payment.getAmount()));
        }

        List<AccountingJournal.Entry> entries = new ArrayList<>(salesEntries);
        entries.addAll(bankEntries);
        entries.addAll(cashEntries);
        return new AccountingJournal(from, to, accounts, entries, sales, receipts);
    }

    /** The entry of a sale; {@code reversed} for a credit note or a cancellation. */
    private void sale(AccountingSettings accounts, Invoice document, LocalDate date, String label, boolean reversed,
                      List<AccountingJournal.Entry> entries) {
        String journal = accounts.getSalesJournal();
        add(entries, journal, date, document, accounts.getCustomerAccount(), label, document.getAmountDue(), reversed);
        add(entries, journal, date, document, accounts.getSalesAccount(), label, document.getSubtotalAmount(), !reversed);
        for (Invoice.VatLine line : document.getVatBreakdown()) {
            add(entries, journal, date, document, accounts.getVatAccount(),
                messages.get(MessageProperties.ACCOUNTING_LABEL_VAT, label, line.rate().stripTrailingZeros().toPlainString()),
                line.amount(), !reversed);
        }
        add(entries, journal, date, document, accounts.getStampAccount(),
            messages.get(MessageProperties.ACCOUNTING_LABEL_STAMP, label), document.getStampDutyAmount(), !reversed);
    }

    /** A debit (or a credit) line; nothing for a zero amount. */
    private static void add(List<AccountingJournal.Entry> entries, String journal, LocalDate date, Invoice document, String account,
                            String label, BigDecimal amount, boolean credit) {
        if (amount == null || amount.signum() == 0) {
            return;
        }
        entries.add(new AccountingJournal.Entry(journal, date, document.getInvoiceNumber(), document.getId(), account,
            clientName(document), label, credit ? BigDecimal.ZERO : amount, credit ? amount : BigDecimal.ZERO));
    }

    private static AccountingJournal.Sale saleRow(Invoice document, LocalDate date, boolean cancellation, boolean negative) {
        BigDecimal sign = negative ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        return new AccountingJournal.Sale(date, document.getId(), document.getInvoiceNumber(), document.getKind(), cancellation,
            clientName(document), document.getClientNif(),
            document.getOriginalInvoice() != null ? document.getOriginalInvoice().getInvoiceNumber() : null,
            document.getSubtotalAmount().multiply(sign), document.getVatAmount().multiply(sign),
            document.getStampDutyAmount().multiply(sign), document.getAmountDue().multiply(sign));
    }

    private static String clientName(Invoice invoice) {
        if (invoice.getClientName() != null) {
            return invoice.getClientName();
        }
        return invoice.getClient() != null ? invoice.getClient().getName() : "";
    }
}
