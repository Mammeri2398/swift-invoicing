package com.swiftlabs.invoicing.vat;

import com.swiftlabs.invoicing.invoice.DocumentKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The TVA of one month, laid out like the G50 declaration needs it: per rate the taxable turnover (HT) and the TVA,
 * the exempt turnover, the droit de timbre collected, and the documents (or payments) behind the figures.
 */
public record VatSummary(int year, int month, VatBasis basis, List<Rate> rates, BigDecimal exemptBase, BigDecimal stampDuty,
                         List<Entry> entries) {

    /** One TVA rate: the taxable amount (HT) and its TVA. */
    public record Rate(BigDecimal rate, BigDecimal base, BigDecimal vat) {
    }

    /** One document (on invoicing) or one payment (on collection); a credit note counts negatively. */
    public record Entry(LocalDate date, Long invoiceId, String number, DocumentKind kind, String client, BigDecimal exclVat,
                        BigDecimal vat, BigDecimal stampDuty, BigDecimal total) {
    }

    /** Taxable turnover: the total of the rates' bases. */
    public BigDecimal taxableBase() {
        return rates.stream().map(Rate::base).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal vat() {
        return rates.stream().map(Rate::vat).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Turnover HT: taxable and exempt. */
    public BigDecimal exclVat() {
        return taxableBase().add(exemptBase);
    }

    public BigDecimal inclVat() {
        return exclVat().add(vat());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
