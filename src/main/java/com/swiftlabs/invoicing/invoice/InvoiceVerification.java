package com.swiftlabs.invoicing.invoice;

import java.time.LocalDate;

/**
 * What the public lookup page may show about an invoice: that it exists, its status and its date.
 * Never the client or the amount (invoice numbers are sequential, hence guessable).
 */
public final class InvoiceVerification {

    private final String number;
    private final InvoiceStatus status;
    private final LocalDate issueDate;

    private InvoiceVerification(String number, InvoiceStatus status, LocalDate issueDate) {
        this.number = number;
        this.status = status;
        this.issueDate = issueDate;
    }

    public static InvoiceVerification of(Invoice invoice) {
        return new InvoiceVerification(invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getIssueDate());
    }

    public String getNumber() {
        return number;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }
}
