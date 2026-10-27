package com.swiftlabs.invoicing.recurring;

import com.swiftlabs.invoicing.common.AmountUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A line copied onto every invoice the model prepares. */
@Entity
@Table(name = "recurring_invoice_line")
public class RecurringInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recurring_invoice_id", nullable = false)
    private RecurringInvoice recurringInvoice;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "unit")
    private String unit;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    protected RecurringInvoiceLine() {
    }

    RecurringInvoiceLine(RecurringInvoice recurringInvoice, int position, String description, String unit, BigDecimal quantity,
                         BigDecimal unitPrice, BigDecimal vatRate) {
        this.recurringInvoice = recurringInvoice;
        this.position = position;
        this.description = description;
        this.unit = unit;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate != null ? vatRate : BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public Integer getPosition() { return position; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getVatRate() { return vatRate; }

    /** Quantity × unit price, before TVA. */
    public BigDecimal getLineTotal() {
        return AmountUtils.round2(quantity.multiply(unitPrice));
    }
}
