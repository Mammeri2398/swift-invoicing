package com.swiftlabs.invoicing.issuer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "issuer")
public class Issuer {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false, length = 20)
    private CompanyType companyType = CompanyType.AUTO_ENTREPRENEUR;

    /** The holder's name for an auto-entrepreneur, the raison sociale for a company. */
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

    /** Auto-entrepreneur only. */
    @Column(name = "ae_card_number")
    private String aeCardNumber;

    /** Auto-entrepreneur: the TVA exemption mention printed on invoices; none: invoice.vat-mention. */
    @Column(name = "vat_mention", length = 255)
    private String vatMention;

    /** Company only: SARL, EURL, SPA... */
    @Column(name = "legal_form", length = 50)
    private String legalForm;

    @Column(name = "share_capital")
    private BigDecimal shareCapital;

    @Column(name = "rc_number", length = 50)
    private String rcNumber;

    /** Article d'imposition (AI). */
    @Column(name = "tax_article", length = 50)
    private String taxArticle;

    @Column(name = "nif", nullable = false)
    private String nif;

    @Column(name = "nis")
    private String nis;

    @Column(name = "activity_description")
    private String activityDescription;

    @Column(name = "bank_or_ccp_account")
    private String bankOrCcpAccount;

    /** Yearly turnover ceiling of the auto-entrepreneur status, chosen by the user. */
    @Column(name = "annual_ceiling", nullable = false)
    private BigDecimal annualCeiling;

    /** PNG or JPEG image, printed on the invoices. */
    @Column(name = "logo_data")
    private byte[] logoData;

    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;

    public Issuer() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyType getCompanyType() {
        return companyType;
    }

    public void setCompanyType(CompanyType companyType) {
        this.companyType = companyType;
    }

    public boolean isCompany() {
        return companyType == CompanyType.COMPANY;
    }

    public String getLegalForm() {
        return legalForm;
    }

    public void setLegalForm(String legalForm) {
        this.legalForm = legalForm;
    }

    public BigDecimal getShareCapital() {
        return shareCapital;
    }

    public void setShareCapital(BigDecimal shareCapital) {
        this.shareCapital = shareCapital;
    }

    public String getRcNumber() {
        return rcNumber;
    }

    public void setRcNumber(String rcNumber) {
        this.rcNumber = rcNumber;
    }

    public String getTaxArticle() {
        return taxArticle;
    }

    public void setTaxArticle(String taxArticle) {
        this.taxArticle = taxArticle;
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

    public String getVatMention() {
        return vatMention;
    }

    public void setVatMention(String vatMention) {
        this.vatMention = vatMention;
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

    public BigDecimal getAnnualCeiling() {
        return annualCeiling;
    }

    public void setAnnualCeiling(BigDecimal annualCeiling) {
        this.annualCeiling = annualCeiling;
    }

    public byte[] getLogoData() {
        return logoData;
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public boolean hasLogo() {
        return logoData != null && logoData.length > 0;
    }

    public void replaceLogo(byte[] data, String contentType) {
        this.logoData = data;
        this.logoContentType = contentType;
    }

    public void removeLogo() {
        this.logoData = null;
        this.logoContentType = null;
    }
}
