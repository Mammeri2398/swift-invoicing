package com.swiftlabs.invoicing.invoice;

/** What a document of the invoice table is. */
public enum DocumentKind {

    INVOICE("Facture"),

    /** Avoir: cancels all or part of an issued invoice, with its own numbering. */
    CREDIT_NOTE("Avoir");

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
