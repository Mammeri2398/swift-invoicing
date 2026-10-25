package com.swiftlabs.invoicing.invoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A payment typed on the invoice page. */
public class PaymentForm {

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    @NotNull
    private LocalDate paidOn;

    private PaymentMethod method;

    @Size(max = 100)
    private String reference;

    public PaymentForm() {
    }

    public PaymentForm(BigDecimal amount, LocalDate paidOn, PaymentMethod method, String reference) {
        this.amount = amount;
        this.paidOn = paidOn;
        this.method = method;
        this.reference = reference;
    }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference != null && !reference.isBlank() ? reference.trim() : null; }
}
