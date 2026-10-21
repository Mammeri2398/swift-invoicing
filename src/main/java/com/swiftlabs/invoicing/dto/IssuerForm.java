package com.swiftlabs.invoicing.dto;

public class IssuerForm {

    private Long id;
    private String fullName;
    private String tradeName;
    private String address;
    private String city;
    private String phone;
    private String email;
    private String aeCardNumber;
    private String nif;
    private String nis;
    private String activityDescription;
    private String bankOrCcpAccount;

    public IssuerForm() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAeCardNumber() { return aeCardNumber; }
    public void setAeCardNumber(String aeCardNumber) { this.aeCardNumber = aeCardNumber; }
    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }
    public String getNis() { return nis; }
    public void setNis(String nis) { this.nis = nis; }
    public String getActivityDescription() { return activityDescription; }
    public void setActivityDescription(String activityDescription) { this.activityDescription = activityDescription; }
    public String getBankOrCcpAccount() { return bankOrCcpAccount; }
    public void setBankOrCcpAccount(String bankOrCcpAccount) { this.bankOrCcpAccount = bankOrCcpAccount; }
}
