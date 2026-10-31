package com.swiftlabs.invoicing.audit;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.PageLinks;
import com.swiftlabs.invoicing.common.Privileges;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Réglages > Journal d'activité: who signed in and out, which pages they saw and what they did. The current
 * company's, or the whole installation's for whoever manages the companies.
 */
@Controller
@RequestMapping(Endpoints.AUDIT)
@PreAuthorize(Privileges.Has.VIEW_AUDIT)
public class AuditController {

    private final AuditService auditService;
    private final int keepDays;

    public AuditController(AuditService auditService, @Value("${app.audit.keep-days:365}") int keepDays) {
        this.auditService = auditService;
        this.keepDays = keepDays;
    }

    /** An invalid filter value in the URL (unknown type, bad date) is ignored rather than failing the page. */
    @GetMapping
    public String list(@ModelAttribute(CommonCodes.Attributes.FILTER) AuditFilter filter, BindingResult ignoredFilterErrors,
                       @PageableDefault(size = 50) Pageable pageable, @AuthenticationPrincipal AppUserPrincipal principal,
                       HttpServletRequest request, Model model) {
        boolean allAllowed = principal.getAuthorities().stream()
            .anyMatch(a -> Privileges.MANAGE_COMPANIES.equals(a.getAuthority()));
        filter.setAll(filter.isAll() && allAllowed);
        model.addAttribute(CommonCodes.Attributes.PAGE,
            auditService.search(filter, filter.isAll() ? null : principal.getCompanyId(), pageable));
        model.addAttribute(CommonCodes.Attributes.PAGE_LINKS, new PageLinks(request));
        model.addAttribute(CommonCodes.Attributes.AUDIT_KINDS, AuditKind.values());
        model.addAttribute(CommonCodes.Attributes.AUDIT_ALL_ALLOWED, allAllowed);
        model.addAttribute(CommonCodes.Attributes.AUDIT_KEEP_DAYS, keepDays);
        return CommonCodes.Views.AUDIT_LIST;
    }
}
