package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Per-year credit note counter, separate from the invoices' one (see InvoiceSequence). */
@Entity
@Table(name = "credit_note_sequence")
public class CreditNoteSequence {

    @Id
    @Column(name = "credit_note_year")
    private Integer year;

    @Column(name = "last_value", nullable = false)
    private Integer lastValue;

    /** Rows are created by CreditNoteSequenceRepository#createIfAbsent, never with new. */
    protected CreditNoteSequence() {
    }

    public Integer getYear() {
        return year;
    }

    public Integer getLastValue() {
        return lastValue;
    }

    public void setLastValue(Integer lastValue) {
        this.lastValue = lastValue;
    }
}
