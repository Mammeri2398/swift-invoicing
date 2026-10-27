package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.IssuerForm;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A new company: its name and the legal identifiers its invoices need from the first one (the same rules as the
 * Paramètres page, by legal status). The rest of its details are completed in its Paramètres page.
 */
public class NewCompanyForm {

    /** The name on its invoices (the person's name for an auto-entrepreneur), and in the company switcher. */
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private CompanyType companyType = CompanyType.COMPANY;

    @NotBlank
    @Size(max = 50)
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

    /** These identifiers over the company's starting details (the placeholders of a new schema). */
    IssuerForm applyTo(IssuerForm form) {
        form.setCompanyType(companyType);
        form.setFullName(name.trim());
        form.setNif(nif);
        form.setNis(blankToNull(nis));
        form.setAddress(blankToNull(address));
        form.setCity(blankToNull(city));
        // Nothing left from the placeholders: an empty phone and email until filled in
        form.setPhone(null);
        form.setEmail(null);
        form.setAeCardNumber(companyType.isCompany() ? null : blankToNull(aeCardNumber));
        form.setLegalForm(companyType.isCompany() ? blankToNull(legalForm) : null);
        form.setShareCapital(companyType.isCompany() ? shareCapital : null);
        form.setRcNumber(companyType.isCompany() ? blankToNull(rcNumber) : null);
        form.setTaxArticle(companyType.isCompany() ? blankToNull(taxArticle) : null);
        return form;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CompanyType getCompanyType() { return companyType; }
    public void setCompanyType(CompanyType companyType) { this.companyType = companyType; }
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
}
