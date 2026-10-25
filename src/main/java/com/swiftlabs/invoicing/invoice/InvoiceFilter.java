package com.swiftlabs.invoicing.invoice;

import java.util.List;

/** Criteria of the invoice list, bound from the query string (?kind=&status=&year=&client=&search=). */
public class InvoiceFilter {

    private DocumentKind kind;
    /** Issued invoices past their due date with something left to pay. */
    private boolean overdue;
    /** The kinds a page shows (set by the controller: the register or the "Devis" page). */
    private List<DocumentKind> kinds;
    private InvoiceStatus status;
    private Integer year;
    private Long client;
    private String search;

    public boolean isOverdue() {
        return overdue;
    }

    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }

    public List<DocumentKind> getKinds() {
        return kinds;
    }

    public void setKinds(List<DocumentKind> kinds) {
        this.kinds = kinds;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public void setKind(DocumentKind kind) {
        this.kind = kind;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Long getClient() {
        return client;
    }

    public void setClient(Long client) {
        this.client = client;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }
}
