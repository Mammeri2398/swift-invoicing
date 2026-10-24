package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.common.AmountUtils;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.List;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", unique = true)
    private String invoiceNumber;

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

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Issuer snapshot (copied at issue time)
    @Column(name = "issuer_full_name")
    private String issuerFullName;

    @Column(name = "issuer_trade_name")
    private String issuerTradeName;

    @Column(name = "issuer_address")
    private String issuerAddress;

    @Column(name = "issuer_ae_card_number")
    private String issuerAeCardNumber;

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

    /** Total = sum of the line totals, rounded to the cent. */
    public BigDecimal recalculateTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            sum = sum.add(line.getLineTotal());
        }
        totalAmount = AmountUtils.round2(sum);
        return totalAmount;
    }
}
