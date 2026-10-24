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
        CREATED, ISSUED, PAID, CANCELLED
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

    protected InvoiceEvent() {
    }

    public InvoiceEvent(Invoice invoice, Action action, String username, LocalDateTime occurredAt) {
        this.invoice = invoice;
        this.action = action;
        this.username = username;
        this.occurredAt = occurredAt;
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
}
