package com.swiftlabs.invoicing.setup;

import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.IssuerForm;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * First-run setup: the administrator account and the company profile. The company fields have
 * the names of IssuerForm, so its validation errors land next to the right fields.
 */
public class SetupForm {

    // Administrator
    @NotBlank
    @Size(min = 3, max = 100)
    @Pattern(regexp = "[A-Za-z0-9._-]*")
    private String adminUsername = "admin";

    @Size(max = 100)
    private String adminFirstName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String adminEmail;

    @NotBlank
    private String password;

    @NotBlank
    private String confirmPassword;

    // Company
    @NotNull
    private CompanyType companyType = CompanyType.AUTO_ENTREPRENEUR;

    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Size(max = 255)
    private String tradeName;

    @NotBlank
    @Size(max = 50)
    // 15 digits, or 20 for the newer identifiers
    @Pattern(regexp = "(\\d{15}|\\d{20})?")
    private String nif;

    @Size(max = 50)
    @Pattern(regexp = "(\\d{15})?")
    private String nis;

    @Size(max = 50)
    private String aeCardNumber;

    @Size(max = 50)
    private String legalForm;

    @DecimalMin("0")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal shareCapital;

    @Size(max = 50)
    private String rcNumber;

    @Size(max = 50)
    private String taxArticle;

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

    @Size(max = 100)
    private String bankOrCcpAccount;

    /** The company part, as the Paramètres page would submit it. */
    IssuerForm toIssuerForm() {
        IssuerForm form = new IssuerForm();
        form.setId(1L);
        form.setCompanyType(companyType);
        form.setFullName(fullName);
        form.setTradeName(tradeName);
        form.setNif(nif);
        form.setNis(nis);
        form.setAeCardNumber(aeCardNumber);
        form.setLegalForm(legalForm);
        form.setShareCapital(shareCapital);
        form.setRcNumber(rcNumber);
        form.setTaxArticle(taxArticle);
        form.setAddress(address);
        form.setCity(city);
        form.setPhone(phone);
        form.setEmail(email);
        form.setBankOrCcpAccount(bankOrCcpAccount);
        // The auto-entrepreneur ceiling, adjustable later in Paramètres
        form.setAnnualCeiling(new BigDecimal("5000000"));
        return form;
    }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getAdminFirstName() { return adminFirstName; }
    public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public CompanyType getCompanyType() { return companyType; }
    public void setCompanyType(CompanyType companyType) { this.companyType = companyType; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }
    public String getNis() { return nis; }
    public void setNis(String nis) { this.nis = nis; }
    public String getAeCardNumber() { return aeCardNumber; }
    public void setAeCardNumber(String aeCardNumber) { this.aeCardNumber = aeCardNumber; }
    public String getLegalForm() { return legalForm; }
    public void setLegalForm(String legalForm) { this.legalForm = legalForm; }
    public BigDecimal getShareCapital() { return shareCapital; }
    public void setShareCapital(BigDecimal shareCapital) { this.shareCapital = shareCapital; }
    public String getRcNumber() { return rcNumber; }
    public void setRcNumber(String rcNumber) { this.rcNumber = rcNumber; }
    public String getTaxArticle() { return taxArticle; }
    public void setTaxArticle(String taxArticle) { this.taxArticle = taxArticle; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBankOrCcpAccount() { return bankOrCcpAccount; }
    public void setBankOrCcpAccount(String bankOrCcpAccount) { this.bankOrCcpAccount = bankOrCcpAccount; }
}
