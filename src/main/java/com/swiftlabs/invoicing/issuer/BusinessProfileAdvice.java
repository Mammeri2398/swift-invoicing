package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.CommonCodes;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes the issuer's legal status to every view as {@code companyType} (header, footer, mentions). */
@ControllerAdvice
public class BusinessProfileAdvice {

    private final IssuerService issuerService;

    public BusinessProfileAdvice(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    @ModelAttribute(CommonCodes.Attributes.COMPANY_TYPE)
    public CompanyType companyType() {
        try {
            return issuerService.getIssuer().getCompanyType();
        } catch (RuntimeException ex) {
            // Never break a page (error pages included) over the header subtitle
            return CompanyType.AUTO_ENTREPRENEUR;
        }
    }
}
