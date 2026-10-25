package com.swiftlabs.invoicing.invoice;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    CANCELLED,
    /** Quotes only: accepted by the client. */
    ACCEPTED,
    /** Quotes only: refused by the client. */
    REFUSED
}
