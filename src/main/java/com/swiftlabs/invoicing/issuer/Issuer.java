package com.swiftlabs.invoicing.issuer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "issuer")
public class Issuer {

    @Id
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "ae_card_number", nullable = false)
    private String aeCardNumber;

    @Column(name = "nif", nullable = false)
    private String nif;

    @Column(name = "nis")
    private String nis;

    @Column(name = "activity_description")
    private String activityDescription;

    @Column(name = "bank_or_ccp_account")
    private String bankOrCcpAccount;

    @Column(name = "logo")
    private String logo;

    public Issuer() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAeCardNumber() {
        return aeCardNumber;
    }

    public void setAeCardNumber(String aeCardNumber) {
        this.aeCardNumber = aeCardNumber;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public String getNis() {
        return nis;
    }

    public void setNis(String nis) {
        this.nis = nis;
    }

    public String getActivityDescription() {
        return activityDescription;
    }

    public void setActivityDescription(String activityDescription) {
        this.activityDescription = activityDescription;
    }

    public String getBankOrCcpAccount() {
        return bankOrCcpAccount;
    }

    public void setBankOrCcpAccount(String bankOrCcpAccount) {
        this.bankOrCcpAccount = bankOrCcpAccount;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }
}
