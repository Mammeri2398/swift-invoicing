package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.issuer.CompanyType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", unique = true)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private DocumentKind kind = DocumentKind.INVOICE;

    /** Invoices created from a quote or a pro forma: that document. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Invoice sourceDocument;

    /** Quotes only. */
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "refused_at")
    private LocalDateTime refusedAt;

    /** Credit notes only: the invoice it cancels, in full or in part. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id")
    private Invoice originalInvoice;

    @Column(name = "invoice_year", nullable = false)
    private Integer invoiceYear;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    @Column(name = "payment_method")
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** Amount to pay: total including TVA (TTC). Equal to the subtotal when no TVA applies. */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Total before TVA (HT): sum of the line totals. */
    @Column(name = "subtotal_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    /** Droit de timbre: invoices paid in cash only. Not part of the turnover nor of the TVA base. */
    @Column(name = "stamp_duty_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal stampDutyAmount = BigDecimal.ZERO;

    // Issuer snapshot (copied at issue time)
    @Column(name = "issuer_full_name")
    private String issuerFullName;

    @Column(name = "issuer_trade_name")
    private String issuerTradeName;

    @Column(name = "issuer_address")
    private String issuerAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuer_company_type", length = 20)
    private CompanyType issuerCompanyType;

    @Column(name = "issuer_ae_card_number")
    private String issuerAeCardNumber;

    @Column(name = "issuer_legal_form", length = 50)
    private String issuerLegalForm;

    @Column(name = "issuer_share_capital", precision = 14, scale = 2)
    private BigDecimal issuerShareCapital;

    @Column(name = "issuer_rc_number", length = 50)
    private String issuerRcNumber;

    @Column(name = "issuer_tax_article", length = 50)
    private String issuerTaxArticle;

    @Column(name = "issuer_nif")
    private String issuerNif;

    @Column(name = "issuer_nis")
    private String issuerNis;

    @Column(name = "issuer_bank_account")
    private String issuerBankAccount;

    @Column(name = "issuer_city")
    private String issuerCity;

    @Column(name = "issuer_phone")
    private String issuerPhone;

    @Column(name = "issuer_email")
    private String issuerEmail;

    // Client snapshot (copied at issue time)
    @Column(name = "client_name")
    private String clientName;

    @Column(name = "client_address")
    private String clientAddress;

    @Column(name = "client_nif")
    private String clientNif;

    @Column(name = "client_rc_number", length = 50)
    private String clientRcNumber;

    @Column(name = "client_nis", length = 50)
    private String clientNis;

    @Column(name = "client_tax_article", length = 50)
    private String clientTaxArticle;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Lines in print order. Removing a line from the list deletes it (orphan removal). */
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    public Invoice() {
        this.status = InvoiceStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.totalAmount = BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Integer getInvoiceYear() {
        return invoiceYear;
    }

    public void setInvoiceYear(Integer invoiceYear) {
        this.invoiceYear = invoiceYear;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
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

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getIssuerFullName() {
        return issuerFullName;
    }

    public void setIssuerFullName(String issuerFullName) {
        this.issuerFullName = issuerFullName;
    }

    public String getIssuerTradeName() {
        return issuerTradeName;
    }

    public void setIssuerTradeName(String issuerTradeName) {
        this.issuerTradeName = issuerTradeName;
    }

    public String getIssuerAddress() {
        return issuerAddress;
    }

    public void setIssuerAddress(String issuerAddress) {
        this.issuerAddress = issuerAddress;
    }

    public String getIssuerAeCardNumber() {
        return issuerAeCardNumber;
    }

    public void setIssuerAeCardNumber(String issuerAeCardNumber) {
        this.issuerAeCardNumber = issuerAeCardNumber;
    }

    public String getIssuerNif() {
        return issuerNif;
    }

    public void setIssuerNif(String issuerNif) {
        this.issuerNif = issuerNif;
    }

    public String getIssuerNis() {
        return issuerNis;
    }

    public void setIssuerNis(String issuerNis) {
        this.issuerNis = issuerNis;
    }

    public String getIssuerBankAccount() {
        return issuerBankAccount;
    }

    public void setIssuerBankAccount(String issuerBankAccount) {
        this.issuerBankAccount = issuerBankAccount;
    }

    public String getIssuerCity() {
        return issuerCity;
    }

    public void setIssuerCity(String issuerCity) {
        this.issuerCity = issuerCity;
    }

    public String getIssuerPhone() {
        return issuerPhone;
    }

    public void setIssuerPhone(String issuerPhone) {
        this.issuerPhone = issuerPhone;
    }

    public String getIssuerEmail() {
        return issuerEmail;
    }

    public void setIssuerEmail(String issuerEmail) {
        this.issuerEmail = issuerEmail;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public String getClientNif() {
        return clientNif;
    }

    public void setClientNif(String clientNif) {
        this.clientNif = clientNif;
    }

    public String getClientRcNumber() {
        return clientRcNumber;
    }

    public void setClientRcNumber(String clientRcNumber) {
        this.clientRcNumber = clientRcNumber;
    }

    public String getClientNis() {
        return clientNis;
    }

    public void setClientNis(String clientNis) {
        this.clientNis = clientNis;
    }

    public String getClientTaxArticle() {
        return clientTaxArticle;
    }

    public void setClientTaxArticle(String clientTaxArticle) {
        this.clientTaxArticle = clientTaxArticle;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public void setKind(DocumentKind kind) {
        this.kind = kind;
    }

    public boolean isCreditNote() {
        return kind == DocumentKind.CREDIT_NOTE;
    }

    public boolean isQuote() {
        return kind == DocumentKind.QUOTE;
    }

    /** A quote or a pro forma: no accounting value. */
    public boolean isOffer() {
        return !kind.isAccounting();
    }

    public Invoice getSourceDocument() {
        return sourceDocument;
    }

    public void setSourceDocument(Invoice sourceDocument) {
        this.sourceDocument = sourceDocument;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getRefusedAt() {
        return refusedAt;
    }

    public void setRefusedAt(LocalDateTime refusedAt) {
        this.refusedAt = refusedAt;
    }

    public Invoice getOriginalInvoice() {
        return originalInvoice;
    }

    public void setOriginalInvoice(Invoice originalInvoice) {
        this.originalInvoice = originalInvoice;
    }

    public boolean isDraft() {
        return status == InvoiceStatus.DRAFT;
    }

    public boolean isIssuedOrPaid() {
        return status == InvoiceStatus.ISSUED || status == InvoiceStatus.PAID;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** Replaces every line (draft editing): positions 1..n in the given order, total recomputed. */
    public void replaceLines(List<InvoiceLine> newLines) {
        lines.clear();
        int position = 1;
        for (InvoiceLine line : newLines) {
            line.setInvoice(this);
            line.setPosition(position++);
            lines.add(line);
        }
        recalculateTotal();
    }

    /**
     * Subtotal (HT) = sum of the line totals; TVA computed per rate on the total of its lines
     * (see {@link #getVatBreakdown()}); total (TTC) = subtotal + TVA. Every amount rounded to the cent.
     */
    public BigDecimal recalculateTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            sum = sum.add(line.getLineTotal());
        }
        subtotalAmount = AmountUtils.round2(sum);
        vatAmount = getVatBreakdown().stream().map(VatLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = subtotalAmount.add(vatAmount);
        return totalAmount;
    }

    /** TVA of one rate: the total of its lines (base HT) and the TVA on that base. */
    public record VatLine(BigDecimal rate, BigDecimal base, BigDecimal amount) {
    }

    /** One entry per TVA rate above 0, highest rate first (empty without TVA). */
    public List<VatLine> getVatBreakdown() {
        Map<BigDecimal, BigDecimal> bases = new TreeMap<>(Comparator.reverseOrder());
        for (InvoiceLine line : lines) {
            BigDecimal rate = line.getVatRate() != null ? line.getVatRate().stripTrailingZeros() : BigDecimal.ZERO;
            if (rate.signum() > 0) {
                bases.merge(rate, line.getLineTotal(), BigDecimal::add);
            }
        }
        List<VatLine> breakdown = new ArrayList<>();
        bases.forEach((rate, base) -> breakdown.add(new VatLine(rate, AmountUtils.round2(base),
            AmountUtils.round2(base.multiply(rate).divide(BigDecimal.valueOf(100))))));
        return breakdown;
    }

    public BigDecimal getStampDutyAmount() {
        return stampDutyAmount;
    }

    public void setStampDutyAmount(BigDecimal stampDutyAmount) {
        this.stampDutyAmount = stampDutyAmount;
    }

    /** What the client pays: the total (TTC) plus the droit de timbre. */
    public BigDecimal getAmountDue() {
        return totalAmount.add(stampDutyAmount);
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public CompanyType getIssuerCompanyType() {
        return issuerCompanyType;
    }

    public void setIssuerCompanyType(CompanyType issuerCompanyType) {
        this.issuerCompanyType = issuerCompanyType;
    }

    public String getIssuerLegalForm() {
        return issuerLegalForm;
    }

    public void setIssuerLegalForm(String issuerLegalForm) {
        this.issuerLegalForm = issuerLegalForm;
    }

    public BigDecimal getIssuerShareCapital() {
        return issuerShareCapital;
    }

    public void setIssuerShareCapital(BigDecimal issuerShareCapital) {
        this.issuerShareCapital = issuerShareCapital;
    }

    public String getIssuerRcNumber() {
        return issuerRcNumber;
    }

    public void setIssuerRcNumber(String issuerRcNumber) {
        this.issuerRcNumber = issuerRcNumber;
    }

    public String getIssuerTaxArticle() {
        return issuerTaxArticle;
    }

    public void setIssuerTaxArticle(String issuerTaxArticle) {
        this.issuerTaxArticle = issuerTaxArticle;
    }
}
