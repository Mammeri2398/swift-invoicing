package com.swiftlabs.invoicing.service;

import com.swiftlabs.invoicing.domain.Client;
import com.swiftlabs.invoicing.domain.ClientType;
import com.swiftlabs.invoicing.domain.Issuer;
import com.swiftlabs.invoicing.domain.Invoice;
import com.swiftlabs.invoicing.domain.InvoiceLine;
import com.swiftlabs.invoicing.domain.InvoiceSequence;
import com.swiftlabs.invoicing.domain.InvoiceStatus;
import com.swiftlabs.invoicing.domain.PaymentMethod;
import com.swiftlabs.invoicing.dto.InvoiceForm;
import com.swiftlabs.invoicing.dto.InvoiceLineForm;
import com.swiftlabs.invoicing.exception.BusinessException;
import com.swiftlabs.invoicing.repository.InvoiceLineRepository;
import com.swiftlabs.invoicing.repository.InvoiceRepository;
import com.swiftlabs.invoicing.repository.InvoiceSequenceRepository;
import com.swiftlabs.invoicing.util.AmountUtils;
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

    private static final String NUMBER_PREFIX = "FA";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceSequenceRepository sequenceRepository;
    private final IssuerService issuerService;
    private final ClientService clientService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceLineRepository invoiceLineRepository,
                          InvoiceSequenceRepository sequenceRepository,
                          IssuerService issuerService,
                          ClientService clientService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.sequenceRepository = sequenceRepository;
        this.issuerService = issuerService;
        this.clientService = clientService;
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

    public boolean existsByNumber(String number) {
        return invoiceRepository.existsByInvoiceNumber(number);
    }

    public Invoice findByNumber(String number) {
        return invoiceRepository.findByInvoiceNumber(number).orElse(null);
    }

    @Transactional
    public Invoice saveDraft(Invoice invoice, List<InvoiceLineForm> lineForms) {
        if (!invoice.isDraft()) {
            throw new BusinessException("Seules les factures brouillon peuvent être modifiées.");
        }
        if (invoice.getClient() == null || invoice.getClient().getId() == null) {
            throw new BusinessException("Un client doit être sélectionné.");
        }
        Client client = clientService.findById(invoice.getClient().getId());
        invoice.setClient(client);

        if (invoice.getIssueDate() == null) {
            invoice.setIssueDate(LocalDate.now());
        }
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

    @Transactional
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

        String number = NUMBER_PREFIX + "-" + year + "-" + String.format("%04d", newValue);
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
        if (year != null && !inv.getInvoiceYear().equals(year)) {
            return false;
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

    public DashboardStats dashboard(Integer year) {
        int y = (year != null ? year : LocalDate.now().getYear());
        BigDecimal total = invoiceRepository.sumIssuedPaidByYear(y);
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        Long ceiling = issuerService.getAnnualCeiling();
        BigDecimal ceilingBd = BigDecimal.valueOf(ceiling);
        BigDecimal percent = ceilingBd.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO : total.multiply(new BigDecimal(100)).divide(ceilingBd, 2, BigDecimal.ROUND_HALF_UP);

        List<Invoice> all = invoiceRepository.findAll();
        List<Invoice> recent = new ArrayList<Invoice>();
        int count = 0;
        for (int i = all.size() - 1; i >= 0 && count < 10; i--) {
            recent.add(all.get(i));
            count++;
        }

        DashboardStats stats = new DashboardStats();
        stats.setYear(y);
        stats.setTotalAmount(total);
        stats.setCeiling(ceilingBd);
        stats.setPercent(percent);
        stats.setWarning80(percent.compareTo(new BigDecimal(80)) >= 0 && percent.compareTo(new BigDecimal(100)) < 0);
        stats.setAlert100(percent.compareTo(new BigDecimal(100)) >= 0);
        stats.setRecentInvoices(recent);
        return stats;
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

    public static class DashboardStats {
        private Integer year;
        private BigDecimal totalAmount;
        private BigDecimal ceiling;
        private BigDecimal percent;
        private boolean warning80;
        private boolean alert100;
        private List<Invoice> recentInvoices = new ArrayList<Invoice>();

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getCeiling() { return ceiling; }
        public void setCeiling(BigDecimal ceiling) { this.ceiling = ceiling; }
        public BigDecimal getPercent() { return percent; }
        public void setPercent(BigDecimal percent) { this.percent = percent; }
        public boolean isWarning80() { return warning80; }
        public void setWarning80(boolean warning80) { this.warning80 = warning80; }
        public boolean isAlert100() { return alert100; }
        public void setAlert100(boolean alert100) { this.alert100 = alert100; }
        public List<Invoice> getRecentInvoices() { return recentInvoices; }
        public void setRecentInvoices(List<Invoice> recentInvoices) { this.recentInvoices = recentInvoices; }
    }
}
