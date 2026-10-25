package com.swiftlabs.invoicing.invoice;

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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

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
        CONVERTED
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
}
