package com.swiftlabs.invoicing.vat;

/**
 * When TVA becomes due, depending on the business (goods or services) and its tax regime: ask the accountant.
 * The summary can be computed either way.
 */
public enum VatBasis {

    /** On invoicing ("régime des débits"): the invoices and credit notes dated in the month. */
    INVOICED,

    /** On collection ("régime des encaissements"): the payments received in the month, TVA in proportion. */
    COLLECTED
}
