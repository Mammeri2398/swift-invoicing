package com.swiftlabs.invoicing.invoice;

import java.util.List;

/** What a document of the invoice table is. */
public enum DocumentKind {

    INVOICE("Facture"),

    /** Avoir: cancels all or part of an issued invoice, with its own numbering. */
    CREDIT_NOTE("Avoir"),

    /** Devis: an offer, accepted or refused by the client, then invoiced. No accounting value. */
    QUOTE("Devis"),

    /** Facture pro forma: a provisional invoice (advance payment, import, purchase order). No accounting value. */
    PROFORMA("Facture pro forma");

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Invoices and credit notes: the documents that count in turnover and TVA. */
    public boolean isAccounting() {
        return this == INVOICE || this == CREDIT_NOTE;
    }

    /** The kinds shown in the invoice register. */
    public static final List<DocumentKind> ACCOUNTING = List.of(INVOICE, CREDIT_NOTE);

    /** The kinds shown on the "Devis" page. */
    public static final List<DocumentKind> OFFERS = List.of(QUOTE, PROFORMA);
}
