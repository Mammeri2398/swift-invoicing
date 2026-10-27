package com.swiftlabs.invoicing.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Accounts and journals of the exported entries (single row, id = 1). Until saved, the usual accounts of the
 * Algerian chart of accounts (SCF) apply: the accountant may use sub-accounts (7061, 5121...) or other journal codes.
 */
@Entity
@Table(name = "accounting_settings")
public class AccountingSettings {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    /** Clients. */
    @Column(name = "customer_account", nullable = false, length = 20)
    private String customerAccount;

    /** Sales: 706 Prestations de services, or 700 Ventes de marchandises. */
    @Column(name = "sales_account", nullable = false, length = 20)
    private String salesAccount;

    /** TVA collectée. */
    @Column(name = "vat_account", nullable = false, length = 20)
    private String vatAccount;

    /** Droit de timbre collected on cash payments, owed to the State. */
    @Column(name = "stamp_account", nullable = false, length = 20)
    private String stampAccount;

    @Column(name = "bank_account", nullable = false, length = 20)
    private String bankAccount;

    @Column(name = "cash_account", nullable = false, length = 20)
    private String cashAccount;

    @Column(name = "sales_journal", nullable = false, length = 10)
    private String salesJournal;

    @Column(name = "bank_journal", nullable = false, length = 10)
    private String bankJournal;

    @Column(name = "cash_journal", nullable = false, length = 10)
    private String cashJournal;

    protected AccountingSettings() {
    }

    /** The SCF defaults. */
    static AccountingSettings defaults() {
        AccountingSettings settings = new AccountingSettings();
        settings.customerAccount = "411";
        settings.salesAccount = "706";
        settings.vatAccount = "4457";
        settings.stampAccount = "447";
        settings.bankAccount = "512";
        settings.cashAccount = "53";
        settings.salesJournal = "VT";
        settings.bankJournal = "BQ";
        settings.cashJournal = "CA";
        return settings;
    }

    public String getCustomerAccount() { return customerAccount; }
    public void setCustomerAccount(String customerAccount) { this.customerAccount = customerAccount; }
    public String getSalesAccount() { return salesAccount; }
    public void setSalesAccount(String salesAccount) { this.salesAccount = salesAccount; }
    public String getVatAccount() { return vatAccount; }
    public void setVatAccount(String vatAccount) { this.vatAccount = vatAccount; }
    public String getStampAccount() { return stampAccount; }
    public void setStampAccount(String stampAccount) { this.stampAccount = stampAccount; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getCashAccount() { return cashAccount; }
    public void setCashAccount(String cashAccount) { this.cashAccount = cashAccount; }
    public String getSalesJournal() { return salesJournal; }
    public void setSalesJournal(String salesJournal) { this.salesJournal = salesJournal; }
    public String getBankJournal() { return bankJournal; }
    public void setBankJournal(String bankJournal) { this.bankJournal = bankJournal; }
    public String getCashJournal() { return cashJournal; }
    public void setCashJournal(String cashJournal) { this.cashJournal = cashJournal; }
}
