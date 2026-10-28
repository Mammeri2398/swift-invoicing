package com.swiftlabs.invoicing.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CatalogItemForm {

    private Long id;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 50)
    private String unit;

    @NotNull
    @DecimalMin("0")
    /** Up to 6 decimals: a monthly fee divided by days (130 000 / 22); line totals are rounded to the centime. */
    @Digits(integer = 12, fraction = 6)
    private BigDecimal unitPrice;

    @NotNull
    private BigDecimal vatRate = BigDecimal.ZERO;

    public static CatalogItemForm from(CatalogItem item) {
        CatalogItemForm form = new CatalogItemForm();
        form.id = item.getId();
        form.name = item.getName();
        form.unit = item.getUnit();
        form.unitPrice = item.getUnitPrice();
        form.vatRate = item.getVatRate();
        return form;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getVatRate() { return vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }
}
