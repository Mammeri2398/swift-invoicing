package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SignedInUser;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Choosing the company to work in (after login, with several), and switching from the header. Open to every
 * signed-in user: what they can choose is their own companies.
 */
@Controller
@RequestMapping(Endpoints.COMPANIES)
public class CompanyController {

    private final CompanyAccessService accessService;
    private final Messages messages;

    public CompanyController(CompanyAccessService accessService, Messages messages) {
        this.accessService = accessService;
        this.messages = messages;
    }

    @GetMapping(Endpoints.COMPANY_SELECT)
    public String select(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute(CommonCodes.Attributes.COMPANIES, accessService.accessesOf(principal.getId()));
        return CommonCodes.Views.COMPANY_SELECT;
    }

    /**
     * Works in another company from now on: the privileges become those of the user's profile there. Back to the
     * dashboard, as the page shown before belongs to the other company.
     */
    @PostMapping(Endpoints.COMPANY_SWITCH)
    public String switchTo(@RequestParam Long companyId, @AuthenticationPrincipal AppUserPrincipal principal,
                           HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        CompanyAccess access;
        try {
            access = accessService.require(principal.getId(), companyId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.COMPANIES + Endpoints.COMPANY_SELECT;
        }
        SignedInUser.replace(principal.withCompany(access), request, response);
        accessService.rememberLast(principal.getId(), companyId);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS,
            messages.get(MessageProperties.COMPANY_SWITCHED, access.getCompany().getName()));
        return Endpoints.REDIRECT + "/";
    }
}
