package com.swiftlabs.invoicing.invoice;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One entry of an invoice's audit trail: who did what, and when. Never modified once written. */
@Entity
@Table(name = "invoice_event")
public class InvoiceEvent {

    /** What happened to the invoice. */
    public enum Action {
        CREATED, ISSUED, PAID, CANCELLED, SENT,
        /** An issued credit note cancelled all or part of the invoice. */
        CREDITED,
        /** Quotes: the client's answer. */
        ACCEPTED, REFUSED,
        /** A quote or a pro forma turned into an invoice. */
        CONVERTED,
        /** A payment received, or removed after a typing mistake. */
        PAYMENT, PAYMENT_DELETED,
        /** A payment reminder sent by email. */
        REMINDED,
        /** A draft saved again: its changes are listed (getChanges). */
        EDITED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private Action action;

    /** Null for events reconstructed from data recorded before the audit trail existed. */
    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** Optional precision, e.g. the recipients of an email. */
    @Column(name = "detail", length = 500)
    private String detail;

    /** What an edit changed, field by field (EDITED events). */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<InvoiceChange> changes = new ArrayList<>();

    protected InvoiceEvent() {
    }

    public InvoiceEvent(Invoice invoice, Action action, String username, LocalDateTime occurredAt, String detail) {
        this.invoice = invoice;
        this.action = action;
        this.username = username;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Action getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getDetail() {
        return detail;
    }

    public List<InvoiceChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    void addChange(InvoiceChange.Field field, Integer lineNumber, String oldValue, String newValue) {
        changes.add(new InvoiceChange(this, changes.size() + 1, field, lineNumber, oldValue, newValue));
    }
}
