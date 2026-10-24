package com.swiftlabs.invoicing.issuer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class IssuerForm {

    private Long id;

    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Size(max = 255)
    private String tradeName;

    @Size(max = 255)
    private String address;

    @Size(max = 255)
    private String city;

    @Size(max = 50)
    @Pattern(regexp = "[0-9+ ().-]*")
    private String phone;

    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(max = 50)
    private String aeCardNumber;

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[0-9]*")
    private String nif;

    @Size(max = 50)
    @Pattern(regexp = "[0-9]*")
    private String nis;

    @Size(max = 500)
    private String activityDescription;

    @Size(max = 100)
    private String bankOrCcpAccount;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal annualCeiling;

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
    public BigDecimal getAnnualCeiling() { return annualCeiling; }
    public void setAnnualCeiling(BigDecimal annualCeiling) { this.annualCeiling = annualCeiling; }

    public static IssuerForm from(Issuer issuer) {
        IssuerForm form = new IssuerForm();
        form.setId(issuer.getId());
        form.setFullName(issuer.getFullName());
        form.setTradeName(issuer.getTradeName());
        form.setAddress(issuer.getAddress());
        form.setCity(issuer.getCity());
        form.setPhone(issuer.getPhone());
        form.setEmail(issuer.getEmail());
        form.setAeCardNumber(issuer.getAeCardNumber());
        form.setNif(issuer.getNif());
        form.setNis(issuer.getNis());
        form.setActivityDescription(issuer.getActivityDescription());
        form.setBankOrCcpAccount(issuer.getBankOrCcpAccount());
        form.setAnnualCeiling(issuer.getAnnualCeiling());
        return form;
    }
}
