package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_sequence")
public class InvoiceSequence {

    @Id
    @Column(name = "invoice_year")
    private Integer year;

    @Column(name = "last_value", nullable = false)
    private Integer lastValue;

    /** Rows are created by InvoiceSequenceRepository#createIfAbsent, never with new. */
    protected InvoiceSequence() {
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getLastValue() {
        return lastValue;
    }

    public void setLastValue(Integer lastValue) {
        this.lastValue = lastValue;
    }
}
