package com.swiftlabs.invoicing.invoice;

/** Criteria of the invoice list, bound from the query string (?status=&year=&client=&search=). */
public class InvoiceFilter {

    private InvoiceStatus status;
    private Integer year;
    private Long client;
    private String search;

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
