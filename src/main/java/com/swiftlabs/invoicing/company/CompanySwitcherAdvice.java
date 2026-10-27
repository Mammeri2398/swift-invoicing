package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.common.CommonCodes;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** The companies the signed-in user may switch to, for the header (shown with more than one). */
@ControllerAdvice
public class CompanySwitcherAdvice {

    private final CompanyAccessService accessService;

    public CompanySwitcherAdvice(CompanyAccessService accessService) {
        this.accessService = accessService;
    }

    @ModelAttribute(CommonCodes.Attributes.COMPANIES)
    public List<CompanyAccess> companies(@CurrentSecurityContext(expression = "authentication") Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return List.of();
        }
        try {
            return accessService.accessesOf(principal.getId());
        } catch (RuntimeException ex) {
            // Never break a page (error pages included) over the switcher
            return List.of();
        }
    }
}
