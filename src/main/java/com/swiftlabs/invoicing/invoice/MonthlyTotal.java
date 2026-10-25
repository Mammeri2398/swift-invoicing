package com.swiftlabs.invoicing.invoice;

import java.math.BigDecimal;

/** Number, total before TVA (HT) and TVA of the documents of one kind and status, issued in one month (1 to 12). */
public record MonthlyTotal(Integer month, DocumentKind kind, InvoiceStatus status, Long count, BigDecimal amount, BigDecimal vat) {
}
