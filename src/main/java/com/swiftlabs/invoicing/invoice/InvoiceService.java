package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository sequenceRepository;
    private final IssuerService issuerService;
    private final ClientService clientService;
    private final InvoiceProperties properties;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceSequenceRepository sequenceRepository,
                          IssuerService issuerService,
                          ClientService clientService,
                          InvoiceProperties properties) {
        this.invoiceRepository = invoiceRepository;
        this.sequenceRepository = sequenceRepository;
        this.issuerService = issuerService;
        this.clientService = clientService;
        this.properties = properties;
    }

    public Invoice newDraft() {
        Invoice invoice = new Invoice();
        invoice.setIssueDate(LocalDate.now());
        invoice.setStatus(InvoiceStatus.DRAFT);
        return invoice;
    }

    public Invoice findById(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() ->
            new ResourceNotFoundException("Facture introuvable (id=" + id + ")."));
    }

    /** Invoice ready to display or print: client and lines loaded in one query. */
    public Invoice findWithLines(Long id) {
        return invoiceRepository.findWithLinesById(id).orElseThrow(() ->
            new ResourceNotFoundException("Facture introuvable (id=" + id + ")."));
    }

    public boolean existsByNumber(String number) {
        return invoiceRepository.existsByInvoiceNumber(number);
    }

    public Invoice findByNumber(String number) {
        return invoiceRepository.findByInvoiceNumber(number).orElse(null);
    }

    /** One page of the invoices matching the filter, filtered and sorted by the database. */
    public Page<Invoice> search(InvoiceFilter filter, Pageable pageable) {
        return invoiceRepository.findAll(InvoiceSpecifications.matching(filter), pageable);
    }

    /** Sum of the totals of every invoice matching the filter, across all pages. */
    public BigDecimal totalOf(InvoiceFilter filter) {
        return invoiceRepository.sumTotal(InvoiceSpecifications.matching(filter));
    }

    /** Creates or updates a draft. The form has been validated (see the constraints on InvoiceForm). */
    @Transactional
    public Invoice saveDraft(InvoiceForm form) {
        // Existing invoice: check the stored status, never the submitted one
        Invoice invoice = form.getId() != null ? findById(form.getId()) : newDraft();
        if (!invoice.isDraft()) {
            throw new BusinessException("Seules les factures brouillon peuvent être modifiées.");
        }
        if (form.getClientId() == null) {
            throw new BusinessException("clientId", "Un client doit être sélectionné.");
        }
        if (form.getLines() == null || form.getLines().isEmpty()) {
            throw new BusinessException("lines", "Une facture doit contenir au moins une ligne.");
        }
        invoice.setClient(clientService.findById(form.getClientId()));
        invoice.setIssueDate(form.getIssueDate() != null ? form.getIssueDate() : LocalDate.now());
        invoice.setDueDate(form.getDueDate());
        invoice.setPaymentMethod(form.getPaymentMethod());
        invoice.setNotes(form.getNotes());
        invoice.replaceLines(form.getLines().stream()
            .map(line -> InvoiceLine.of(line.getDescription(), line.getUnit(), line.getQuantity(), line.getUnitPrice()))
            .toList());
        // Lines are saved with the invoice (cascade); removed lines are deleted (orphan removal)
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice issue(Long id) {
        Invoice invoice = findById(id);
        if (!invoice.isDraft()) {
            throw new BusinessException("Cette facture est déjà émise et ne peut plus être modifiée.");
        }
        Client client = invoice.getClient();
        if (client.getType() == ClientType.PROFESSIONNEL && (client.getNif() == null || client.getNif().isBlank())) {
            throw new BusinessException("Le NIF du client professionnel est obligatoire avant d'émettre la facture.");
        }
        if (invoice.getLines().isEmpty()) {
            throw new BusinessException("Une facture doit contenir au moins une ligne pour être émise.");
        }
        int year = invoice.getIssueDate().getYear();

        // Concurrent issues of the same year queue on the counter's row lock: numbers stay
        // unique and gap-free, and the first invoice of a year cannot fail on the row creation.
        sequenceRepository.createIfAbsent(year);
        InvoiceSequence sequence = sequenceRepository.lockByYear(year);
        int number = sequence.getLastValue() + 1;
        sequence.setLastValue(number);

        String invoiceNumber = properties.numberPrefix() + "-" + year + "-" + String.format("%04d", number);
        if (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
            throw new BusinessException("Le numéro de facture " + invoiceNumber + " existe déjà.");
        }

        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceYear(year);
        invoice.setSequenceNumber(number);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.recalculateTotal();
        snapshotIssuer(invoice);
        snapshotClient(invoice, client);
        return invoice;
    }

    @Transactional
    public Invoice pay(Long id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessException("Seule une facture émise peut être marquée payée.");
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoice;
    }

    @Transactional
    public Invoice cancel(Long id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("Cette facture est déjà annulée.");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(LocalDateTime.now());
        return invoice;
    }

    /** Deletes a draft and its lines (cascade). */
    @Transactional
    public void delete(Long id) {
        Invoice invoice = findById(id);
        if (!invoice.isDraft()) {
            throw new BusinessException("Seules les factures brouillon peuvent être supprimées.");
        }
        invoiceRepository.delete(invoice);
    }

    /** Turnover of the year: issued and paid invoices only. */
    public BigDecimal totalBilled(int year) {
        BigDecimal total = invoiceRepository.sumIssuedPaidByYear(year);
        return total != null ? total : BigDecimal.ZERO;
    }

    public long countByStatus(InvoiceStatus status) {
        return invoiceRepository.countByStatus(status);
    }

    public BigDecimal totalByStatus(InvoiceStatus status) {
        BigDecimal total = invoiceRepository.sumByStatus(status);
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<Invoice> findRecent() {
        return invoiceRepository.findTop10ByOrderByCreatedAtDescIdDesc();
    }

    private void snapshotIssuer(Invoice invoice) {
        Issuer issuer = issuerService.getIssuer();
        invoice.setIssuerFullName(issuer.getFullName());
        invoice.setIssuerTradeName(issuer.getTradeName());
        invoice.setIssuerAddress(issuer.getAddress());
        invoice.setIssuerCity(issuer.getCity());
        invoice.setIssuerPhone(issuer.getPhone());
        invoice.setIssuerEmail(issuer.getEmail());
        invoice.setIssuerAeCardNumber(issuer.getAeCardNumber());
        invoice.setIssuerNif(issuer.getNif());
        invoice.setIssuerNis(issuer.getNis());
        invoice.setIssuerBankAccount(issuer.getBankOrCcpAccount());
    }

    private void snapshotClient(Invoice invoice, Client client) {
        invoice.setClientName(client.getName());
        invoice.setClientAddress(client.getAddress());
        invoice.setClientNif(client.getNif());
    }
}
