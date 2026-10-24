package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.issuer.Issuer;

/**
 * Issuer details printed on an invoice: the snapshot taken at issue time, or the current issuer
 * profile for a draft. Never a mix of both, field by field.
 */
public final class IssuerDetails {

    private final String fullName;
    private final String tradeName;
    private final String address;
    private final String city;
    private final String phone;
    private final String email;
    private final String aeCardNumber;
    private final String nif;
    private final String nis;
    private final String bankAccount;

    private IssuerDetails(String fullName, String tradeName, String address, String city, String phone, String email,
                          String aeCardNumber, String nif, String nis, String bankAccount) {
        this.fullName = fullName;
        this.tradeName = tradeName;
        this.address = address;
        this.city = city;
        this.phone = phone;
        this.email = email;
        this.aeCardNumber = aeCardNumber;
        this.nif = nif;
        this.nis = nis;
        this.bankAccount = bankAccount;
    }

    public static IssuerDetails of(Invoice invoice, Issuer current) {
        if (invoice.isDraft()) {
            return new IssuerDetails(current.getFullName(), current.getTradeName(), current.getAddress(), current.getCity(),
                current.getPhone(), current.getEmail(), current.getAeCardNumber(), current.getNif(), current.getNis(),
                current.getBankOrCcpAccount());
        }
        return new IssuerDetails(invoice.getIssuerFullName(), invoice.getIssuerTradeName(), invoice.getIssuerAddress(),
            invoice.getIssuerCity(), invoice.getIssuerPhone(), invoice.getIssuerEmail(), invoice.getIssuerAeCardNumber(),
            invoice.getIssuerNif(), invoice.getIssuerNis(), invoice.getIssuerBankAccount());
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
