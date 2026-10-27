package com.swiftlabs.invoicing.accounting;

import com.swiftlabs.invoicing.invoice.DocumentKind;
import com.swiftlabs.invoicing.invoice.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the accountant needs for a period: the entries (sales journal, bank and cash journals), and the documents and
 * payments behind them. Every piece is balanced: its debits equal its credits.
 */
public record AccountingJournal(LocalDate from, LocalDate to, AccountingSettings accounts, List<Entry> entries,
                                List<Sale> sales, List<Receipt> receipts) {

    /** One line of an entry: a debit or a credit on one account. */
    public record Entry(String journal, LocalDate date, String piece, Long invoiceId, String account, String party,
                        String label, BigDecimal debit, BigDecimal credit) {
    }

    /**
     * An invoice or credit note issued in the period, or the cancellation of an invoice (dated the day it was
     * cancelled). Credit notes and cancellations are negative, so that a column sum is the net amount.
     */
    public record Sale(LocalDate date, Long invoiceId, String number, DocumentKind kind, boolean cancellation, String client,
                       String clientNif, String originalInvoice, BigDecimal exclVat, BigDecimal vat, BigDecimal stampDuty,
                       BigDecimal total) {
    }

    /** A payment received in the period, and the journal it goes to (cash or bank). */
    public record Receipt(LocalDate date, Long invoiceId, String number, String client, PaymentMethod method, String reference,
                          String journal, BigDecimal amount) {
    }

    public BigDecimal totalDebit() {
        return entries.stream().map(Entry::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredit() {
        return entries.stream().map(Entry::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isBalanced() {
        return totalDebit().compareTo(totalCredit()) == 0;
    }

    /** Net sales of the period, droit de timbre included (what the clients owe). */
    public BigDecimal salesTotal() {
        return sales.stream().map(Sale::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal receiptsTotal() {
        return receipts.stream().map(Receipt::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
