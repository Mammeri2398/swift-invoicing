package com.swiftlabs.invoicing.invoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class InvoiceLineForm {

    private Long id;

    @NotBlank
    @Size(max = 255)
    private String description;

    @Size(max = 50)
    private String unit;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 9, fraction = 3)
    private BigDecimal quantity;

    @NotNull
    @DecimalMin("0")
    /** Up to 6 decimals: a monthly fee divided by days (130 000 / 22); line totals are rounded to the centime. */
    @Digits(integer = 12, fraction = 6)
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    /** Percent; must be one of invoice.vat-rates for a company, 0 for an auto-entrepreneur (see InvoiceService). */
    private BigDecimal vatRate;

    public InvoiceLineForm() {
        this.quantity = BigDecimal.ZERO;
        this.unitPrice = BigDecimal.ZERO;
        this.lineTotal = BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(BigDecimal vatRate) {
        this.vatRate = vatRate;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public static InvoiceLineForm from(InvoiceLine line) {
        InvoiceLineForm form = new InvoiceLineForm();
        form.setId(line.getId());
        form.setDescription(line.getDescription());
        form.setUnit(line.getUnit());
        form.setQuantity(line.getQuantity());
        form.setUnitPrice(line.getUnitPrice());
        form.setLineTotal(line.getLineTotal());
        form.setVatRate(line.getVatRate());
        return form;
    }
}
