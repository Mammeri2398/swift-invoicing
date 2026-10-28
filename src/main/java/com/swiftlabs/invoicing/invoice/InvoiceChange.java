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

/**
 * One field changed by a draft's edit, in the history: its old and new value, stored without formatting (plain
 * numbers, ISO dates, codes, text as typed) and formatted for the reader by {@link InvoiceAudit#changes}.
 */
@Entity
@Table(name = "invoice_event_change")
public class InvoiceChange {

    /** What changed, and how its values are written. */
    public enum Field {
        CLIENT(Kind.TEXT), ISSUE_DATE(Kind.DATE), DUE_DATE(Kind.DATE), PAYMENT_METHOD(Kind.PAYMENT_METHOD), NOTES(Kind.TEXT),
        LINE_ADDED(Kind.TEXT), LINE_REMOVED(Kind.TEXT), LINE_DESCRIPTION(Kind.TEXT), LINE_UNIT(Kind.TEXT),
        LINE_QUANTITY(Kind.QUANTITY), LINE_UNIT_PRICE(Kind.PRICE), LINE_VAT_RATE(Kind.RATE), LINE_TOTAL(Kind.AMOUNT),
        SUBTOTAL(Kind.AMOUNT), VAT(Kind.AMOUNT), STAMP_DUTY(Kind.AMOUNT), AMOUNT_DUE(Kind.AMOUNT);

        public enum Kind { TEXT, DATE, PAYMENT_METHOD, QUANTITY, PRICE, RATE, AMOUNT }

        private final Kind kind;

        Field(Kind kind) {
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }

        public boolean isLine() {
            return name().startsWith("LINE_");
        }
    }

    static final int MAX_VALUE = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private InvoiceEvent event;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 30)
    private Field field;

    /** The line concerned (1, 2...), for the fields of a line. */
    @Column(name = "line_number")
    private Integer lineNumber;

    /** Null: the value did not exist (an added line, a due date set). */
    @Column(name = "old_value", length = MAX_VALUE)
    private String oldValue;

    /** Null: the value no longer exists (a removed line, a due date cleared). */
    @Column(name = "new_value", length = MAX_VALUE)
    private String newValue;

    protected InvoiceChange() {
    }

    InvoiceChange(InvoiceEvent event, int position, Field field, Integer lineNumber, String oldValue, String newValue) {
        this.event = event;
        this.position = position;
        this.field = field;
        this.lineNumber = lineNumber;
        this.oldValue = truncate(oldValue);
        this.newValue = truncate(newValue);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_VALUE ? value : value.substring(0, MAX_VALUE);
    }

    public Field getField() { return field; }
    public Integer getLineNumber() { return lineNumber; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
}
