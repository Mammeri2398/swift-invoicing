package com.swiftlabs.invoicing.recurring;

import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Recurring invoices: the models, and the drafts they prepare. A draft is never issued automatically: the user checks
 * it (quantities, a price that changed) and issues it as any other invoice.
 */
@Service
public class RecurringInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(RecurringInvoiceService.class);

    /** Drafts prepared for one model in one run: a PC off for months does not flood the register. */
    static final int MAX_CATCH_UP = 12;

    private final RecurringInvoiceRepository repository;
    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RecurringInvoiceService(RecurringInvoiceRepository repository, InvoiceService invoiceService, ClientService clientService,
                                   PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecurringInvoice> findAll() {
        return repository.findAllWithClient();
    }

    @Transactional(readOnly = true)
    public RecurringInvoice findById(Long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResourceNotFoundException(MessageProperties.RECURRING_ERROR_NOT_FOUND, String.valueOf(id)));
    }

    @Transactional(readOnly = true)
    public RecurringInvoiceForm form(Long id) {
        return RecurringInvoiceForm.from(findById(id));
    }

    /** A model prepared from an invoice, not saved yet. */
    @Transactional(readOnly = true)
    public RecurringInvoiceForm formFromInvoice(Long invoiceId) {
        return RecurringInvoiceForm.fromInvoice(invoiceService.findWithLines(invoiceId));
    }

    @Transactional
    public RecurringInvoice save(RecurringInvoiceForm form) {
        RecurringInvoice model = form.getId() != null ? findById(form.getId()) : new RecurringInvoice(null);
        if (form.getEndDate() != null && form.getEndDate().isBefore(form.getNextDate())) {
            throw BusinessException.onField("endDate", MessageProperties.RECURRING_ERROR_END_BEFORE_NEXT);
        }
        List<RecurringInvoiceLine> lines = new ArrayList<>();
        for (int i = 0; i < form.getLines().size(); i++) {
            InvoiceLineForm line = form.getLines().get(i);
            // Same TVA rules as an invoice, checked now rather than at the first draft
            lines.add(new RecurringInvoiceLine(model, i + 1, line.getDescription().trim(), blankToNull(line.getUnit()),
                line.getQuantity(), line.getUnitPrice(), invoiceService.lineVatRate(line.getVatRate(), i)));
        }
        model.setName(form.getName().trim());
        model.setClient(clientService.findById(form.getClientId()));
        // A new date or a new frequency: the dates are counted again from the next one
        if (model.getId() == null || !form.getNextDate().equals(model.getNextDate()) || form.getFrequency() != model.getFrequency()) {
            model.restartOn(form.getNextDate());
        }
        model.setFrequency(form.getFrequency());
        model.setEndDate(form.getEndDate());
        model.setDueDays(form.getDueDays());
        model.setPaymentMethod(form.getPaymentMethod());
        model.setNotes(blankToNull(form.getNotes()));
        model.setLastError(null);
        model.replaceLines(lines);
        return repository.save(model);
    }

    @Transactional
    public RecurringInvoice setActive(Long id, boolean active) {
        RecurringInvoice model = findById(id);
        model.setActive(active);
        return model;
    }

    /** Deletes the model; the invoices it prepared stay. */
    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
    }

    /**
     * Prepares the drafts of every model that is due, dated on their dates (a PC that was off catches up, up to
     * {@link #MAX_CATCH_UP} per model). Each draft is saved in its own transaction: a model that fails (its TVA rate no
     * longer allowed...) keeps its error for the list and does not stop the others.
     *
     * @return the number of drafts prepared
     */
    public int generateDue() {
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        for (Long id : transaction.execute(status -> repository.findByActiveTrueOrderById().stream()
                .filter(model -> model.isDue(today)).map(RecurringInvoice::getId).toList())) {
            for (int i = 0; i < MAX_CATCH_UP; i++) {
                try {
                    Invoice draft = transaction.execute(status -> prepareNext(id, today));
                    if (draft == null) {
                        break;
                    }
                    created++;
                } catch (RuntimeException ex) {
                    // Any failure stays with its model: the other models (and companies) are still prepared
                    if (ex instanceof BusinessException) {
                        log.warn("Recurring invoice {}: no draft prepared: {}", id, ex.getMessage());
                    } else {
                        log.error("Recurring invoice {}: no draft prepared", id, ex);
                    }
                    recordFailure(id, ex);
                    break;
                }
            }
        }
        if (created > 0) {
            log.info("Recurring invoices: {} draft(s) prepared", created);
        }
        return created;
    }

    private void recordFailure(Long id, RuntimeException failure) {
        try {
            transaction.executeWithoutResult(status -> repository.findById(id).ifPresent(model -> {
                model.setLastRunAt(LocalDateTime.now(clock));
                model.setLastError(truncate(String.valueOf(failure.getMessage())));
            }));
        } catch (RuntimeException ex) {
            log.error("Recurring invoice {}: the failure could not be recorded either", id, ex);
        }
    }

    /** The draft of the model's next date, if due; the model then moves to the following date. */
    private Invoice prepareNext(Long id, LocalDate today) {
        RecurringInvoice model = repository.findById(id).orElse(null);
        if (model == null || !model.isDue(today)) {
            return null;
        }
        Invoice draft = invoiceService.saveDraft(invoiceForm(model, model.getNextDate()));
        model.advance();
        model.setLastInvoice(draft);
        model.setLastRunAt(LocalDateTime.now(clock));
        model.setLastError(null);
        return draft;
    }

    private static InvoiceForm invoiceForm(RecurringInvoice model, LocalDate date) {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(model.getClient().getId());
        form.setIssueDate(date);
        form.setDueDate(model.getDueDays() != null ? date.plusDays(model.getDueDays()) : null);
        form.setPaymentMethod(model.getPaymentMethod());
        form.setNotes(model.getNotes());
        for (RecurringInvoiceLine line : model.getLines()) {
            InvoiceLineForm lineForm = new InvoiceLineForm();
            lineForm.setDescription(line.getDescription());
            lineForm.setUnit(line.getUnit());
            lineForm.setQuantity(line.getQuantity());
            lineForm.setUnitPrice(line.getUnitPrice());
            lineForm.setVatRate(line.getVatRate());
            form.getLines().add(lineForm);
        }
        return form;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String message) {
        return message == null || message.length() <= 500 ? message : message.substring(0, 500);
    }
}
