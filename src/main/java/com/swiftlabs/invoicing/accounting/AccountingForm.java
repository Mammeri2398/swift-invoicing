package com.swiftlabs.invoicing.accounting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The accounts and journals of Comptabilité > Comptes: digits for an account, letters and digits for a journal. */
public class AccountingForm {

    private static final String ACCOUNT = "\\d{1,20}";
    private static final String JOURNAL = "[A-Za-z0-9]{1,10}";

    @NotBlank @Size(max = 20) @Pattern(regexp = ACCOUNT)
    private String customerAccount;

    @NotBlank @Size(max = 20) @Pattern(regexp = ACCOUNT)
    private String salesAccount;

    @NotBlank @Size(max = 20) @Pattern(regexp = ACCOUNT)
    private String vatAccount;

    @NotBlank @Size(max = 20) @Pattern(regexp = ACCOUNT)
    private String stampAccount;

    @NotBlank @Size(max = 20) @Pattern(regexp = ACCOUNT)
    private String bankAccount;

    @NotBlank @Size(max = 20) @Pattern(regexp = ACCOUNT)
    private String cashAccount;

    @NotBlank @Size(max = 10) @Pattern(regexp = JOURNAL)
    private String salesJournal;

    @NotBlank @Size(max = 10) @Pattern(regexp = JOURNAL)
    private String bankJournal;

    @NotBlank @Size(max = 10) @Pattern(regexp = JOURNAL)
    private String cashJournal;

    public static AccountingForm from(AccountingSettings settings) {
        AccountingForm form = new AccountingForm();
        form.customerAccount = settings.getCustomerAccount();
        form.salesAccount = settings.getSalesAccount();
        form.vatAccount = settings.getVatAccount();
        form.stampAccount = settings.getStampAccount();
        form.bankAccount = settings.getBankAccount();
        form.cashAccount = settings.getCashAccount();
        form.salesJournal = settings.getSalesJournal();
        form.bankJournal = settings.getBankJournal();
        form.cashJournal = settings.getCashJournal();
        return form;
    }

    void applyTo(AccountingSettings settings) {
        settings.setCustomerAccount(customerAccount.trim());
        settings.setSalesAccount(salesAccount.trim());
        settings.setVatAccount(vatAccount.trim());
        settings.setStampAccount(stampAccount.trim());
        settings.setBankAccount(bankAccount.trim());
        settings.setCashAccount(cashAccount.trim());
        settings.setSalesJournal(salesJournal.trim().toUpperCase());
        settings.setBankJournal(bankJournal.trim().toUpperCase());
        settings.setCashJournal(cashJournal.trim().toUpperCase());
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
