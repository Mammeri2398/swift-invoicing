package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceSequenceRepository sequenceRepository;
    private final IssuerService issuerService;
    private final ClientService clientService;
    private final InvoiceProperties properties;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceLineRepository invoiceLineRepository,
                          InvoiceSequenceRepository sequenceRepository,
                          IssuerService issuerService,
                          ClientService clientService,
                          InvoiceProperties properties) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
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
            new BusinessException("Facture introuvable (id=" + id + ")."));
    }

    /** Invoice with its lines loaded (lines are not mapped on the entity). */
    public Invoice findWithLines(Long id) {
        Invoice invoice = findById(id);
        invoice.setLines(invoiceLineRepository.findByInvoiceIdOrderByPositionAsc(id));
        return invoice;
    }

    public boolean existsByNumber(String number) {
        return invoiceRepository.existsByInvoiceNumber(number);
    }

    public Invoice findByNumber(String number) {
        return invoiceRepository.findByInvoiceNumber(number).orElse(null);
    }

    @Transactional
    public Invoice saveDraft(InvoiceForm form) {
        // Existing invoice: check the stored status, never the submitted one
        Invoice invoice = form.getId() != null ? findById(form.getId()) : newDraft();
        if (!invoice.isDraft()) {
            throw new BusinessException("Seules les factures brouillon peuvent être modifiées.");
        }
        if (form.getClientId() == null) {
            throw new BusinessException("Un client doit être sélectionné.");
        }
        invoice.setClient(clientService.findById(form.getClientId()));
        invoice.setIssueDate(form.getIssueDate() != null ? form.getIssueDate() : LocalDate.now());
        invoice.setDueDate(form.getDueDate());
        invoice.setPaymentMethod(form.getPaymentMethod() != null && !form.getPaymentMethod().isEmpty()
            ? PaymentMethod.valueOf(form.getPaymentMethod()) : null);
        invoice.setNotes(form.getNotes());
        List<InvoiceLineForm> lineForms = form.getLines() != null ? form.getLines() : List.of();
        invoice.setTotalAmount(BigDecimal.ZERO);
        Invoice saved = invoiceRepository.save(invoice);

        invoiceLineRepository.deleteByInvoiceId(saved.getId());

        List<InvoiceLine> lines = new ArrayList<InvoiceLine>();
        int position = 1;
        for (InvoiceLineForm lf : lineForms) {
            if (lf.getDescription() == null || lf.getDescription().trim().isEmpty()) {
                continue;
            }
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(saved);
            line.setPosition(position);
            line.setDescription(lf.getDescription().trim());
            line.setUnit(lf.getUnit());
            line.setQuantity(lf.getQuantity());
            line.setUnitPrice(lf.getUnitPrice());
            line.setLineTotal(AmountUtils.lineTotal(lf.getQuantity(), lf.getUnitPrice()));
            lines.add(line);
            position++;
        }
        if (lines.isEmpty()) {
            throw new BusinessException("Une facture doit contenir au moins une ligne.");
        }
        for (InvoiceLine line : lines) {
            invoiceLineRepository.save(line);
        }
        saved.setLines(lines);
        saved.setTotalAmount(computeTotal(lines));
        return invoiceRepository.save(saved);
    }

    @Transactional
    public Invoice issue(Long id) {
        Invoice invoice = findById(id);
        if (!invoice.isDraft()) {
            throw new BusinessException("Cette facture est déjà émise et ne peut plus être modifiée.");
        }
        if (invoice.getClient() == null || invoice.getClient().getId() == null) {
            throw new BusinessException("Un client doit être sélectionné.");
        }
        Client client = clientService.findById(invoice.getClient().getId());
        if (client.getType() == ClientType.PROFESSIONNEL
            && (client.getNif() == null || client.getNif().trim().isEmpty())) {
            throw new BusinessException("Le NIF du client professionnel est obligatoire avant d'émettre la facture.");
        }

        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderByPositionAsc(invoice.getId());
        if (lines.isEmpty()) {
            throw new BusinessException("Une facture doit contenir au moins une ligne pour être émise.");
        }

        int year = (invoice.getIssueDate() != null ? invoice.getIssueDate().getYear() : LocalDate.now().getYear());

        return issueWithinTransaction(invoice, client, lines, year);
    }

    private Invoice issueWithinTransaction(Invoice invoice, Client client,
                                           List<InvoiceLine> lines, int year) {
        InvoiceSequence seq = sequenceRepository.findByYear(year);
        if (seq == null) {
            seq = new InvoiceSequence(year, 0);
            sequenceRepository.save(seq);
            seq = sequenceRepository.lockByYear(year);
        } else {
            seq = sequenceRepository.lockByYear(year);
        }
        int newValue = seq.getLastValue() + 1;
        seq.setLastValue(newValue);
        sequenceRepository.save(seq);

        String number = properties.numberPrefix() + "-" + year + "-" + String.format("%04d", newValue);
        if (invoiceRepository.existsByInvoiceNumber(number)) {
            throw new BusinessException("Le numéro de facture " + number + " existe déjà.");
        }

        invoice.setInvoiceNumber(number);
        invoice.setInvoiceYear(year);
        invoice.setSequenceNumber(newValue);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setTotalAmount(computeTotal(lines));

        snapshotIssuer(invoice);
        snapshotClient(invoice, client);

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice pay(Long id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessException("Seule une facture émise peut être marquée payée.");
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice cancel(Long id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("Cette facture est déjà annulée.");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = findById(id);
        if (!invoice.isDraft()) {
            throw new BusinessException("Seules les factures brouillon peuvent être supprimées.");
        }
        invoiceLineRepository.deleteByInvoiceId(id);
        invoiceRepository.delete(invoice);
    }

    public List<Invoice> list(String status, Integer year, Long clientId, String search) {
        List<Invoice> result = invoiceRepository.findAll();
        List<Invoice> filtered = new ArrayList<Invoice>();
        for (Invoice inv : result) {
            if (matches(inv, status, year, clientId, search)) {
                filtered.add(inv);
            }
        }
        return filtered;
    }

    private boolean matches(Invoice inv, String status, Integer year, Long clientId, String search) {
        if (status != null && !status.isEmpty() && !inv.getStatus().name().equals(status)) {
            return false;
        }
        if (year != null) {
            // Drafts have no invoice year yet: fall back to the issue date
            Integer invYear = inv.getInvoiceYear() != null ? inv.getInvoiceYear()
                : (inv.getIssueDate() != null ? Integer.valueOf(inv.getIssueDate().getYear()) : null);
            if (!year.equals(invYear)) {
                return false;
            }
        }
        if (clientId != null && (inv.getClient() == null || !inv.getClient().getId().equals(clientId))) {
            return false;
        }
        if (search != null && !search.isEmpty()) {
            String s = search.toLowerCase();
            String number = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber().toLowerCase() : "";
            String clientName = inv.getClient() != null ? inv.getClient().getName().toLowerCase() : "";
            String snapshotClient = inv.getClientName() != null ? inv.getClientName().toLowerCase() : "";
            if (!number.contains(s) && !clientName.contains(s) && !snapshotClient.contains(s)) {
                return false;
            }
        }
        return true;
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
        invoice.setIssuerAeCardNumber(issuer.getAeCardNumber());
        invoice.setIssuerNif(issuer.getNif());
        invoice.setIssuerNis(issuer.getNis());
        invoice.setIssuerBankAccount(issuer.getBankOrCcpAccount());
        invoice.setIssuerCity(issuer.getCity());
        invoice.setIssuerPhone(issuer.getPhone());
        invoice.setIssuerEmail(issuer.getEmail());
    }

    private void snapshotClient(Invoice invoice, Client client) {
        invoice.setClientName(client.getName());
        invoice.setClientAddress(client.getAddress());
        invoice.setClientNif(client.getNif());
    }

    private BigDecimal computeTotal(List<InvoiceLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            total = total.add(line.getLineTotal());
        }
        return AmountUtils.round2(total);
    }
}
