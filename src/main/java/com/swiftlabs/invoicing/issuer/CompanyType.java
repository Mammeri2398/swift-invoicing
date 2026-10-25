package com.swiftlabs.invoicing.issuer;

/** Legal status of the issuer: decides the identifiers printed on invoices and whether TVA applies. */
public enum CompanyType {

    /** Auto-entrepreneur (ANAE card): no TVA, annual turnover ceiling. */
    AUTO_ENTREPRENEUR("Auto-entrepreneur"),

    /** Company (SARL, EURL, SPA...): RC, article d'imposition, capital, TVA per line. */
    COMPANY("Société");

    private final String label;

    CompanyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCompany() {
        return this == COMPANY;
    }
}
