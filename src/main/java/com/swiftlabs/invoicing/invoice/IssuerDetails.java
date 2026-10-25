package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.Issuer;

import java.math.BigDecimal;

/**
 * Issuer details shown on an invoice: the snapshot taken at issue time, or the current issuer
 * profile for a draft. Never a mix of both, field by field.
 */
public final class IssuerDetails {

    private final CompanyType companyType;
    private final String fullName;
    private final String tradeName;
    private final String address;
    private final String city;
    private final String phone;
    private final String email;
    private final String aeCardNumber;
    private final String legalForm;
    private final BigDecimal shareCapital;
    private final String rcNumber;
    private final String taxArticle;
    private final String nif;
    private final String nis;
    private final String bankAccount;

    private IssuerDetails(CompanyType companyType, String fullName, String tradeName, String address, String city,
                          String phone, String email, String aeCardNumber, String legalForm, BigDecimal shareCapital,
                          String rcNumber, String taxArticle, String nif, String nis, String bankAccount) {
        // Invoices issued before the type existed were all issued by an auto-entrepreneur
        this.companyType = companyType != null ? companyType : CompanyType.AUTO_ENTREPRENEUR;
        this.fullName = fullName;
        this.tradeName = tradeName;
        this.address = address;
        this.city = city;
        this.phone = phone;
        this.email = email;
        this.aeCardNumber = aeCardNumber;
        this.legalForm = legalForm;
        this.shareCapital = shareCapital;
        this.rcNumber = rcNumber;
        this.taxArticle = taxArticle;
        this.nif = nif;
        this.nis = nis;
        this.bankAccount = bankAccount;
    }

    public static IssuerDetails of(Invoice invoice, Issuer current) {
        if (invoice.isDraft()) {
            return new IssuerDetails(current.getCompanyType(), current.getFullName(), current.getTradeName(),
                current.getAddress(), current.getCity(), current.getPhone(), current.getEmail(), current.getAeCardNumber(),
                current.getLegalForm(), current.getShareCapital(), current.getRcNumber(), current.getTaxArticle(),
                current.getNif(), current.getNis(), current.getBankOrCcpAccount());
        }
        return new IssuerDetails(invoice.getIssuerCompanyType(), invoice.getIssuerFullName(), invoice.getIssuerTradeName(),
            invoice.getIssuerAddress(), invoice.getIssuerCity(), invoice.getIssuerPhone(), invoice.getIssuerEmail(),
            invoice.getIssuerAeCardNumber(), invoice.getIssuerLegalForm(), invoice.getIssuerShareCapital(),
            invoice.getIssuerRcNumber(), invoice.getIssuerTaxArticle(), invoice.getIssuerNif(), invoice.getIssuerNis(),
            invoice.getIssuerBankAccount());
    }

    public CompanyType getCompanyType() {
        return companyType;
    }

    /** A company: TVA columns and totals, company identifiers. */
    public boolean isCompany() {
        return companyType.isCompany();
    }

    public String getFullName() {
        return fullName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAeCardNumber() {
        return aeCardNumber;
    }

    public String getLegalForm() {
        return legalForm;
    }

    public BigDecimal getShareCapital() {
        return shareCapital;
    }

    public String getRcNumber() {
        return rcNumber;
    }

    public String getTaxArticle() {
        return taxArticle;
    }

    public String getNif() {
        return nif;
    }

    public String getNis() {
        return nis;
    }

    public String getBankAccount() {
        return bankAccount;
    }
}
