package com.swiftlabs.invoicing.invoice;

import java.time.LocalDate;

/**
 * What the public lookup page may show about an invoice: that it exists, who issued it, its status and its date.
 * Never the client or the amount (invoice numbers are sequential, hence guessable). With several companies in the
 * installation, the issuer tells apart documents with the same number; {@code invoiceId} is set only when the visitor
 * may open it (signed in, in its company, allowed to view invoices).
 */
public final class InvoiceVerification {

    private final DocumentKind kind;
    private final String number;
    private final InvoiceStatus status;
    private final LocalDate issueDate;
    private final String issuer;
    private final Long invoiceId;

    private InvoiceVerification(DocumentKind kind, String number, InvoiceStatus status, LocalDate issueDate, String issuer,
                                Long invoiceId) {
        this.kind = kind;
        this.number = number;
        this.status = status;
        this.issueDate = issueDate;
        this.issuer = issuer;
        this.invoiceId = invoiceId;
    }

    /** {@code openable}: the visitor may open the invoice (its id is then kept, for the link). */
    public static InvoiceVerification of(Invoice invoice, boolean openable) {
        String issuer = invoice.getIssuerTradeName() != null ? invoice.getIssuerTradeName() : invoice.getIssuerFullName();
        return new InvoiceVerification(invoice.getKind(), invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getIssueDate(),
            issuer, openable ? invoice.getId() : null);
    }

    public boolean isCreditNote() {
        return kind == DocumentKind.CREDIT_NOTE;
    }

    /** Invoice, credit note, quote or pro forma (the page shows its label in the visitor's language). */
    public DocumentKind getKind() {
        return kind;
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

    /** The issuer's name as printed on the document (its trade name if any). */
    public String getIssuer() {
        return issuer;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }
}
