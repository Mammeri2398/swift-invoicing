package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.AmountUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@DueDateNotBeforeIssueDate
public class InvoiceForm {

    private Long id;
    /** The version the form was filled from, to refuse a save over a change made meanwhile. */
    private Long version;

    /** For a new document: invoice, quote or pro forma (credit notes come from an invoice). */
    private DocumentKind kind;

    @NotNull
    private Long clientId;

    @NotNull
    private LocalDate issueDate;
    private LocalDate dueDate;
    private PaymentMethod paymentMethod;

    @Size(max = 1000)
    private String notes;

    @NotEmpty
    @Valid
    private List<InvoiceLineForm> lines = new ArrayList<InvoiceLineForm>();

    public InvoiceForm() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public void setKind(DocumentKind kind) {
        this.kind = kind;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<InvoiceLineForm> getLines() {
        return lines;
    }

    public void setLines(List<InvoiceLineForm> lines) {
        this.lines = lines;
    }

    public void addEmptyLine() {
        this.lines.add(new InvoiceLineForm());
    }

    public BigDecimal getTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (InvoiceLineForm line : lines) {
                if (line.getLineTotal() != null) {
                    total = total.add(line.getLineTotal());
                }
            }
        }
        return AmountUtils.round2(total);
    }

    /** Form for editing a draft; the invoice must have its lines loaded. */
    public static InvoiceForm from(Invoice invoice) {
        InvoiceForm form = new InvoiceForm();
        form.setId(invoice.getId());
        form.setVersion(invoice.getVersion());
        form.setKind(invoice.getKind());
        form.setClientId(invoice.getClient() != null ? invoice.getClient().getId() : null);
        form.setIssueDate(invoice.getIssueDate());
        form.setDueDate(invoice.getDueDate());
        form.setPaymentMethod(invoice.getPaymentMethod());
        form.setNotes(invoice.getNotes());
        if (invoice.getLines() == null || invoice.getLines().isEmpty()) {
            form.addEmptyLine();
        }
        for (InvoiceLine line : invoice.getLines()) {
            form.getLines().add(InvoiceLineForm.from(line));
        }
        return form;
    }
}
