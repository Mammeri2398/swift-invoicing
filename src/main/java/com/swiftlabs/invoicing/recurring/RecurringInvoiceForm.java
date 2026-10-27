package com.swiftlabs.invoicing.recurring;

import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The model of a recurring invoice; its lines are validated like those of an invoice. */
public class RecurringInvoiceForm {

    private Long id;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private Long clientId;

    @NotNull
    private Frequency frequency = Frequency.MONTHLY;

    /** Date of the next invoice; changing it restarts the count from it. */
    @NotNull
    private LocalDate nextDate;

    private LocalDate endDate;

    @Min(0)
    @Max(365)
    private Integer dueDays;

    private PaymentMethod paymentMethod;

    @Size(max = 1000)
    private String notes;

    @NotEmpty
    @Valid
    private List<InvoiceLineForm> lines = new ArrayList<>();

    public static RecurringInvoiceForm from(RecurringInvoice model) {
        RecurringInvoiceForm form = new RecurringInvoiceForm();
        form.id = model.getId();
        form.name = model.getName();
        form.clientId = model.getClient().getId();
        form.frequency = model.getFrequency();
        form.nextDate = model.getNextDate();
        form.endDate = model.getEndDate();
        form.dueDays = model.getDueDays();
        form.paymentMethod = model.getPaymentMethod();
        form.notes = model.getNotes();
        for (RecurringInvoiceLine line : model.getLines()) {
            InvoiceLineForm lineForm = new InvoiceLineForm();
            lineForm.setDescription(line.getDescription());
            lineForm.setUnit(line.getUnit());
            lineForm.setQuantity(line.getQuantity());
            lineForm.setUnitPrice(line.getUnitPrice());
            lineForm.setVatRate(line.getVatRate());
            lineForm.setLineTotal(line.getLineTotal());
            form.lines.add(lineForm);
        }
        return form;
    }

    /** A model from an invoice ("Rendre récurrente"): its client, lines and terms, the next invoice a period later. */
    public static RecurringInvoiceForm fromInvoice(Invoice invoice) {
        RecurringInvoiceForm form = new RecurringInvoiceForm();
        form.clientId = invoice.getClient() != null ? invoice.getClient().getId() : null;
        form.name = invoice.getClientName() != null ? invoice.getClientName() : (invoice.getClient() != null ? invoice.getClient().getName() : null);
        form.nextDate = invoice.getIssueDate().plusMonths(1);
        if (invoice.getDueDate() != null) {
            form.dueDays = (int) Math.max(0, Math.min(365, invoice.getDueDate().toEpochDay() - invoice.getIssueDate().toEpochDay()));
        }
        form.paymentMethod = invoice.getPaymentMethod();
        form.notes = invoice.getNotes();
        invoice.getLines().forEach(line -> form.lines.add(InvoiceLineForm.from(line)));
        form.lines.forEach(line -> line.setId(null));
        return form;
    }

    public void addEmptyLine() {
        lines.add(new InvoiceLineForm());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getDueDays() { return dueDays; }
    public void setDueDays(Integer dueDays) { this.dueDays = dueDays; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<InvoiceLineForm> getLines() { return lines; }
    public void setLines(List<InvoiceLineForm> lines) { this.lines = lines; }
}
