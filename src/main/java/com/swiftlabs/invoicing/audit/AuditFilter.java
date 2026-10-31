package com.swiftlabs.invoicing.audit;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Criteria of the activity journal, bound from the query string (?user=&kind=&from=&to=&search=&all=). */
public class AuditFilter {

    /** Part of the user name. */
    private String user;
    private AuditKind kind;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    /** Part of what it is about (invoice number, client...), of the address, of the code that answered, or of the IP address. */
    private String search;
    /** Every company of the installation, for whoever manages them; otherwise the current company only. */
    private boolean all;

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public AuditKind getKind() { return kind; }
    public void setKind(AuditKind kind) { this.kind = kind; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
    public boolean isAll() { return all; }
    public void setAll(boolean all) { this.all = all; }
}
