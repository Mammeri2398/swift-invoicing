package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.config.InvoiceProperties;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the issuer's legal status to every view as {@code companyType} (header, footer, mentions), and the TVA
 * exemption mention of an auto-entrepreneur as {@code vatMention} (the company's own, else invoice.vat-mention).
 */
@ControllerAdvice
public class BusinessProfileAdvice {

    private final IssuerService issuerService;
    private final InvoiceProperties properties;

    public BusinessProfileAdvice(IssuerService issuerService, InvoiceProperties properties) {
        this.issuerService = issuerService;
        this.properties = properties;
    }

    @ModelAttribute(CommonCodes.Attributes.COMPANY_TYPE)
    public CompanyType companyType() {
        // No company yet (choosing one): nothing to read
        if (CompanyContext.currentCompanyId() == null) {
            return CompanyType.AUTO_ENTREPRENEUR;
        }
        try {
            return issuerService.getIssuer().getCompanyType();
        } catch (RuntimeException ex) {
            // Never break a page (error pages included) over the header subtitle
            return CompanyType.AUTO_ENTREPRENEUR;
        }
    }

    @ModelAttribute(CommonCodes.Attributes.VAT_MENTION)
    public String vatMention() {
        if (CompanyContext.currentCompanyId() == null) {
            return properties.vatMention();
        }
        try {
            String mention = issuerService.getIssuer().getVatMention();
            return mention != null ? mention : properties.vatMention();
        } catch (RuntimeException ex) {
            // No issuer yet (first setup): the default
            return properties.vatMention();
        }
    }
}
