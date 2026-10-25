package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository sequenceRepository;
    private final CreditNoteSequenceRepository creditNoteSequenceRepository;
    private final DocumentSequenceRepository documentSequenceRepository;
    private final IssuerService issuerService;
    private final ClientService clientService;
    private final InvoiceAudit audit;
    private final PaymentService paymentService;
    private final InvoiceProperties properties;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceSequenceRepository sequenceRepository,
                          CreditNoteSequenceRepository creditNoteSequenceRepository,
                          DocumentSequenceRepository documentSequenceRepository,
                          IssuerService issuerService,
                          ClientService clientService,
                          InvoiceAudit audit,
                          PaymentService paymentService,
                          InvoiceProperties properties) {
        this.invoiceRepository = invoiceRepository;
        this.sequenceRepository = sequenceRepository;
        this.creditNoteSequenceRepository = creditNoteSequenceRepository;
        this.documentSequenceRepository = documentSequenceRepository;
        this.issuerService = issuerService;
        this.clientService = clientService;
        this.audit = audit;
        this.paymentService = paymentService;
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

    /** Every invoice matching the filter, not paginated (exports). */
    public List<Invoice> searchAll(InvoiceFilter filter, Sort sort) {
        return invoiceRepository.findAll(InvoiceSpecifications.matching(filter), sort);
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
        // A new document is an invoice, a quote or a pro forma; credit notes come from createCreditNote
        if (form.getId() == null && form.getKind() != null && !form.getKind().isAccounting()) {
            invoice.setKind(form.getKind());
        }
        if (!invoice.isDraft()) {
            throw new BusinessException("Seules les factures brouillon peuvent être modifiées.");
        }
        if (form.getClientId() == null && !invoice.isCreditNote()) {
            throw new BusinessException("clientId", "Un client doit être sélectionné.");
        }
        if (form.getLines() == null || form.getLines().isEmpty()) {
            throw new BusinessException("lines", "Une facture doit contenir au moins une ligne.");
        }
        // A credit note is always for the client of its invoice
        if (!invoice.isCreditNote()) {
            invoice.setClient(clientService.findById(form.getClientId()));
        }
        invoice.setIssueDate(form.getIssueDate() != null ? form.getIssueDate() : LocalDate.now());
        invoice.setDueDate(form.getDueDate());
        invoice.setPaymentMethod(form.getPaymentMethod());
        invoice.setNotes(form.getNotes());
        Issuer issuer = issuerService.getIssuer();
        List<InvoiceLine> lines = new ArrayList<>();
        for (int i = 0; i < form.getLines().size(); i++) {
            InvoiceLineForm line = form.getLines().get(i);
            lines.add(InvoiceLine.of(line.getDescription(), line.getUnit(), line.getQuantity(), line.getUnitPrice(),
                vatRate(issuer, line.getVatRate(), i)));
        }
        invoice.replaceLines(lines);
        invoice.setStampDutyAmount(stampDuty(invoice));
        boolean created = invoice.getId() == null;
        // Lines are saved with the invoice (cascade); removed lines are deleted (orphan removal)
        Invoice saved = invoiceRepository.save(invoice);
        if (created) {
            audit.record(saved, InvoiceEvent.Action.CREATED);
        }
        return saved;
    }

    @Transactional
    public Invoice issue(Long id) {
        Invoice invoice = findById(id);
        if (!invoice.isDraft()) {
            throw new BusinessException("Cette facture est déjà émise et ne peut plus être modifiée.");
        }
        if (invoice.isCreditNote()) {
            checkCreditNote(invoice);
        }
        Client client = invoice.getClient();
        // Quotes and pro formas are not fiscal documents: the client's identifiers are checked on the invoice
        boolean fiscal = invoice.getKind().isAccounting();
        if (fiscal && client.getType() == ClientType.PROFESSIONNEL && (client.getNif() == null || client.getNif().isBlank())) {
            throw new BusinessException("Le NIF du client professionnel est obligatoire avant d'émettre la facture.");
        }
        if (invoice.getLines().isEmpty()) {
            throw new BusinessException("Une facture doit contenir au moins une ligne pour être émise.");
        }
        Issuer issuer = issuerService.getIssuer();
        // A company's invoice to a business carries the buyer's identifiers too (décret exécutif 05-468)
        if (fiscal && issuer.isCompany() && client.getType() == ClientType.PROFESSIONNEL) {
            List<String> missing = new ArrayList<>();
            if (isBlank(client.getRcNumber())) {
                missing.add("le registre du commerce (RC)");
            }
            if (isBlank(client.getTaxArticle())) {
                missing.add("l'article d'imposition (AI)");
            }
            if (!missing.isEmpty()) {
                throw new BusinessException("Complétez la fiche du client avant d'émettre la facture : "
                    + String.join(" et ", missing) + (missing.size() > 1 ? " manquent." : " manque."));
            }
        }
        // The type may have changed since the draft was saved
        if (!issuer.isCompany() && invoice.getLines().stream().anyMatch(line -> line.getVatRate().signum() > 0)) {
            throw new BusinessException("Cette facture contient de la TVA alors que le profil émetteur est auto-entrepreneur : "
                + "modifiez le brouillon pour la retirer.");
        }
        int year = invoice.getIssueDate().getYear();
        int number = nextNumber(invoice.getKind(), year);
        String prefix = prefix(invoice.getKind());
        String invoiceNumber = prefix + "-" + year + "-" + String.format("%04d", number);
        if (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
            throw new BusinessException("Le numéro " + invoiceNumber + " existe déjà.");
        }

        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceYear(year);
        invoice.setSequenceNumber(number);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.recalculateTotal();
        invoice.setStampDutyAmount(stampDuty(invoice));
        snapshotIssuer(invoice, issuer);
        snapshotClient(invoice, client);
        if (invoice.isCreditNote()) {
            Invoice original = invoice.getOriginalInvoice();
            audit.record(invoice, InvoiceEvent.Action.ISSUED, "sur la facture " + original.getInvoiceNumber());
            audit.record(original, InvoiceEvent.Action.CREDITED,
                invoiceNumber + " (" + AmountUtils.format(invoice.getTotalAmount()) + ")");
            // Partly paid then credited for the rest: nothing left to pay
            paymentService.settleIfPaid(original);
        } else {
            audit.record(invoice, InvoiceEvent.Action.ISSUED);
        }
        return invoice;
    }

    /**
     * Next number of the year for this kind of document. Concurrent issues of the same year queue
     * on the counter's row lock: numbers stay unique and gap-free, and the first document of a
     * year cannot fail on the row creation.
     */
    private int nextNumber(DocumentKind kind, int year) {
        if (!kind.isAccounting()) {
            return documentSequenceRepository.next(kind, year);
        }
        if (kind == DocumentKind.CREDIT_NOTE) {
            creditNoteSequenceRepository.createIfAbsent(year);
            CreditNoteSequence sequence = creditNoteSequenceRepository.lockByYear(year);
            sequence.setLastValue(sequence.getLastValue() + 1);
            return sequence.getLastValue();
        }
        sequenceRepository.createIfAbsent(year);
        InvoiceSequence sequence = sequenceRepository.lockByYear(year);
        sequence.setLastValue(sequence.getLastValue() + 1);
        return sequence.getLastValue();
    }

    private String prefix(DocumentKind kind) {
        return switch (kind) {
            case INVOICE -> properties.numberPrefix();
            case CREDIT_NOTE -> properties.creditNotePrefix();
            case QUOTE -> properties.quotePrefix();
            case PROFORMA -> properties.proformaPrefix();
        };
    }

    /** The client accepted an issued quote. */
    @Transactional
    public Invoice accept(Long quoteId) {
        Invoice quote = issuedQuote(quoteId);
        quote.setStatus(InvoiceStatus.ACCEPTED);
        quote.setAcceptedAt(LocalDateTime.now());
        audit.record(quote, InvoiceEvent.Action.ACCEPTED);
        return quote;
    }

    /** The client refused an issued quote. */
    @Transactional
    public Invoice refuse(Long quoteId) {
        Invoice quote = issuedQuote(quoteId);
        quote.setStatus(InvoiceStatus.REFUSED);
        quote.setRefusedAt(LocalDateTime.now());
        audit.record(quote, InvoiceEvent.Action.REFUSED);
        return quote;
    }

    private Invoice issuedQuote(Long quoteId) {
        Invoice quote = findById(quoteId);
        if (!quote.isQuote() || quote.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessException("Seul un devis émis peut être accepté ou refusé.");
        }
        return quote;
    }

    /**
     * Turns an issued quote (accepted or not yet answered) or pro forma into a draft invoice with the
     * same client and lines, linked to it. An issued quote not answered yet becomes accepted. When
     * the document already has an invoice (not cancelled), that invoice is returned instead.
     */
    @Transactional
    public Invoice convertToInvoice(Long documentId) {
        Invoice document = findWithLines(documentId);
        boolean convertible = document.isOffer() && (document.getStatus() == InvoiceStatus.ISSUED
            || (document.isQuote() && document.getStatus() == InvoiceStatus.ACCEPTED));
        if (!convertible) {
            throw new BusinessException("Seul un devis émis ou accepté, ou une pro forma émise, peut être facturé.");
        }
        Invoice existing = invoiceOf(documentId);
        if (existing != null) {
            return existing;
        }
        if (document.isQuote() && document.getStatus() == InvoiceStatus.ISSUED) {
            accept(documentId);
        }
        Invoice invoice = newDraft();
        invoice.setSourceDocument(document);
        invoice.setClient(document.getClient());
        invoice.setPaymentMethod(document.getPaymentMethod());
        String reference = (document.isQuote() ? "devis " : "facture pro forma ") + document.getInvoiceNumber()
            + " du " + document.getIssueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        invoice.setNotes(document.getNotes() != null ? document.getNotes() : "Selon " + reference + ".");
        invoice.replaceLines(document.getLines().stream()
            .map(line -> InvoiceLine.of(line.getDescription(), line.getUnit(), line.getQuantity(), line.getUnitPrice(),
                line.getVatRate()))
            .toList());
        invoice.setStampDutyAmount(stampDuty(invoice));
        Invoice saved = invoiceRepository.save(invoice);
        audit.record(saved, InvoiceEvent.Action.CREATED, "depuis le " + reference);
        audit.record(document, InvoiceEvent.Action.CONVERTED, "facture brouillon créée");
        return saved;
    }

    /** The invoice created from a quote or a pro forma (cancelled ones ignored), or null. */
    public Invoice invoiceOf(Long documentId) {
        return invoiceRepository.findBySourceDocumentIdOrderByIdDesc(documentId).stream()
            .filter(invoice -> invoice.getStatus() != InvoiceStatus.CANCELLED)
            .findFirst().orElse(null);
    }

    /**
     * New credit note for an issued or paid invoice: a draft with a copy of its lines (full credit),
     * which the user can reduce for a partial credit before issuing. An existing draft credit note
     * of the invoice is returned instead of creating a second one.
     */
    @Transactional
    public Invoice createCreditNote(Long invoiceId) {
        Invoice original = findWithLines(invoiceId);
        if (original.getKind() != DocumentKind.INVOICE || !original.isIssuedOrPaid()) {
            throw new BusinessException("Un avoir ne peut être établi que sur une facture émise ou payée.");
        }
        List<Invoice> existing = invoiceRepository.findByOriginalInvoiceIdOrderByIdAsc(invoiceId);
        for (Invoice creditNote : existing) {
            if (creditNote.isDraft()) {
                return creditNote;
            }
        }
        if (remainingToCredit(original).signum() <= 0) {
            throw new BusinessException("Cette facture est déjà entièrement annulée par avoir.");
        }
        Invoice creditNote = newDraft();
        creditNote.setKind(DocumentKind.CREDIT_NOTE);
        creditNote.setOriginalInvoice(original);
        creditNote.setClient(original.getClient());
        creditNote.setPaymentMethod(original.getPaymentMethod());
        creditNote.setNotes("Avoir sur la facture " + original.getInvoiceNumber() + " du "
            + original.getIssueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        creditNote.replaceLines(original.getLines().stream()
            .map(line -> InvoiceLine.of(line.getDescription(), line.getUnit(), line.getQuantity(), line.getUnitPrice(),
                line.getVatRate()))
            .toList());
        Invoice saved = invoiceRepository.save(creditNote);
        audit.record(saved, InvoiceEvent.Action.CREATED, "sur la facture " + original.getInvoiceNumber());
        return saved;
    }

    /** Credit notes of an invoice (drafts included), oldest first. */
    public List<Invoice> creditNotesOf(Long invoiceId) {
        return invoiceRepository.findByOriginalInvoiceIdOrderByIdAsc(invoiceId);
    }

    /** TTC of the invoice not cancelled yet by issued credit notes. */
    public BigDecimal remainingToCredit(Invoice invoice) {
        return invoice.getTotalAmount().subtract(invoiceRepository.sumIssuedCreditNotes(invoice.getId()));
    }

    /** A credit note never cancels more than what remains of its invoice, nor predates it. */
    private void checkCreditNote(Invoice creditNote) {
        Invoice original = creditNote.getOriginalInvoice();
        if (!original.isIssuedOrPaid()) {
            throw new BusinessException("La facture " + original.getInvoiceNumber() + " n'est plus émise : l'avoir ne peut pas être émis.");
        }
        if (creditNote.getIssueDate().isBefore(original.getIssueDate())) {
            throw new BusinessException("issueDate", "L'avoir ne peut pas être daté avant la facture qu'il annule.");
        }
        creditNote.recalculateTotal();
        BigDecimal remaining = remainingToCredit(original);
        if (creditNote.getTotalAmount().compareTo(remaining) > 0) {
            throw new BusinessException("Le montant de l'avoir (" + AmountUtils.format(creditNote.getTotalAmount())
                + ") dépasse le reste à annuler sur la facture " + original.getInvoiceNumber()
                + " (" + AmountUtils.format(remaining) + ") : réduisez les lignes.");
        }
    }

    @Transactional
    public Invoice pay(Long id) {
        Invoice invoice = findById(id);
        if (invoice.isCreditNote()) {
            throw new BusinessException("Un avoir ne se marque pas payé.");
        }
        if (invoice.isOffer()) {
            throw new BusinessException("Un devis ou une facture pro forma ne se marque pas payé : facturez-le d'abord.");
        }
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessException("Seule une facture émise peut être marquée payée.");
        }
        if (remainingToCredit(invoice).signum() <= 0) {
            throw new BusinessException("Cette facture est entièrement annulée par avoir : il n'y a rien à encaisser.");
        }
        // "Payer le solde": one payment of everything left, today, with the invoice's payment method
        paymentService.record(id, new PaymentForm(paymentService.balance(invoice), LocalDate.now(), invoice.getPaymentMethod(), null));
        return invoice;
    }

    @Transactional
    public Invoice cancel(Long id) {
        Invoice invoice = findById(id);
        if (invoice.isCreditNote() && !invoice.isDraft()) {
            throw new BusinessException("Un avoir émis ne peut pas être annulé.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("Cette facture est déjà annulée.");
        }
        // Cancelling on top of a credit note would cancel the same amount twice
        if (invoiceRepository.sumIssuedCreditNotes(invoice.getId()).signum() > 0) {
            throw new BusinessException("Cette facture a déjà un avoir : établissez un nouvel avoir pour annuler le reste.");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(LocalDateTime.now());
        audit.record(invoice, InvoiceEvent.Action.CANCELLED);
        return invoice;
    }

    /** Deletes a draft, its lines (cascade) and its history. */
    @Transactional
    public void delete(Long id) {
        Invoice invoice = findById(id);
        if (!invoice.isDraft()) {
            throw new BusinessException("Seules les factures brouillon peuvent être supprimées.");
        }
        audit.forget(invoice);
        invoiceRepository.delete(invoice);
    }

    /** Turnover of the year: issued and paid invoices only. */
    public BigDecimal totalBilled(int year) {
        BigDecimal total = invoiceRepository.sumIssuedPaidByYear(year);
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<MonthlyTotal> monthlyTotals(int year) {
        return invoiceRepository.monthlyTotals(year);
    }

    public List<Integer> invoiceYears() {
        return invoiceRepository.findInvoiceYears();
    }

    /** Issued invoices with something left to pay (credit notes and payments deducted). */
    public long countUnpaid() {
        return paymentService.countUnpaid();
    }

    /** What clients still owe on issued invoices, credit notes and payments deducted. */
    public BigDecimal totalUnpaid() {
        return paymentService.totalUnpaid();
    }

    /** Draft invoices and credit notes (quotes and pro formas apart). */
    public long countDrafts() {
        return invoiceRepository.countByKindInAndStatus(DocumentKind.ACCOUNTING, InvoiceStatus.DRAFT);
    }


    public List<Invoice> findRecent() {
        return invoiceRepository.findTop10ByKindInOrderByCreatedAtDescIdDesc(DocumentKind.ACCOUNTING);
    }

    /** Droit de timbre: on invoices paid in cash, never on a credit note. */
    private BigDecimal stampDuty(Invoice invoice) {
        if (invoice.getKind() != DocumentKind.INVOICE || invoice.getPaymentMethod() != PaymentMethod.CASH) {
            return BigDecimal.ZERO;
        }
        return properties.stampDuty().compute(invoice.getTotalAmount());
    }

    /** TVA rate of a line: none for an auto-entrepreneur, one of invoice.vat-rates for a company. */
    private BigDecimal vatRate(Issuer issuer, BigDecimal requested, int index) {
        if (!issuer.isCompany()) {
            return BigDecimal.ZERO;
        }
        String field = "lines[" + index + "].vatRate";
        if (requested == null) {
            throw new BusinessException(field, "Choisissez un taux de TVA.");
        }
        if (properties.vatRates().stream().noneMatch(rate -> rate.compareTo(requested) == 0)) {
            throw new BusinessException(field, "Taux de TVA non autorisé.");
        }
        return requested;
    }

    private void snapshotIssuer(Invoice invoice, Issuer issuer) {
        invoice.setIssuerCompanyType(issuer.getCompanyType());
        invoice.setIssuerLegalForm(issuer.getLegalForm());
        invoice.setIssuerShareCapital(issuer.getShareCapital());
        invoice.setIssuerRcNumber(issuer.getRcNumber());
        invoice.setIssuerTaxArticle(issuer.getTaxArticle());
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
        invoice.setClientRcNumber(client.getRcNumber());
        invoice.setClientNis(client.getNis());
        invoice.setClientTaxArticle(client.getTaxArticle());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
