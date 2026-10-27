package com.swiftlabs.invoicing.recurring;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.PaymentMethod;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A recurring invoice: the model of an invoice that comes back every month, quarter, half-year or year. At each date
 * the application prepares a draft invoice from it, to check and issue as usual.
 *
 * The dates are counted from {@code startDate}: the n-th invoice is dated {@code startDate + n × frequency}, so a model
 * starting on the 31st gives the last day of the shorter months and the 31st again after them.
 */
@Entity
@Table(name = "recurring_invoice")
public class RecurringInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** For the user only ("Loyer bureau", "Maintenance site web"): never printed. */
    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private Frequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Invoices prepared since {@code startDate}. */
    @Column(name = "occurrences", nullable = false)
    private int occurrences;

    /** No invoice dated after it; none: no end. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Due date = invoice date + this many days; none: no due date. */
    @Column(name = "due_days")
    private Integer dueDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** Paused: no invoice is prepared until it is resumed. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** The latest draft prepared (cleared if that draft is deleted). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_invoice_id")
    private Invoice lastInvoice;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /** Why the latest attempt failed (client deleted, TVA rate no longer allowed...), in French; none once it works. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "recurringInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RecurringInvoiceLine> lines = new ArrayList<>();

    protected RecurringInvoice() {
    }

    RecurringInvoice(Client client) {
        this.client = client;
    }

    /** The date of the next invoice to prepare. */
    public LocalDate getNextDate() {
        return startDate.plusMonths((long) occurrences * frequency.getMonths());
    }

    /** Ended: its next date is past the end date. */
    public boolean isFinished() {
        return endDate != null && getNextDate().isAfter(endDate);
    }

    /** Due on {@code today}: active, not finished, and its next date reached. */
    public boolean isDue(LocalDate today) {
        return active && !isFinished() && !getNextDate().isAfter(today);
    }

    /** Moves to the following date, once an invoice is prepared for the current one. */
    void advance() {
        occurrences++;
    }

    /** A new first date: the count starts again from it. */
    void restartOn(LocalDate date) {
        startDate = date;
        occurrences = 0;
    }

    void replaceLines(List<RecurringInvoiceLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
    }

    /** Total before TVA, for the list. */
    public BigDecimal getSubtotal() {
        return lines.stream().map(RecurringInvoiceLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    void setName(String name) { this.name = name; }
    public Client getClient() { return client; }
    void setClient(Client client) { this.client = client; }
    public Frequency getFrequency() { return frequency; }
    void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public LocalDate getStartDate() { return startDate; }
    public int getOccurrences() { return occurrences; }
    public LocalDate getEndDate() { return endDate; }
    void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getDueDays() { return dueDays; }
    void setDueDays(Integer dueDays) { this.dueDays = dueDays; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getNotes() { return notes; }
    void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    void setActive(boolean active) { this.active = active; }
    public Invoice getLastInvoice() { return lastInvoice; }
    void setLastInvoice(Invoice lastInvoice) { this.lastInvoice = lastInvoice; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastError() { return lastError; }
    void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<RecurringInvoiceLine> getLines() { return Collections.unmodifiableList(lines); }
}
