package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SignedInUser;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.licence.LicenceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Réglages > Sociétés: the companies of the installation, for whoever manages them. */
@Controller
@RequestMapping(Endpoints.COMPANY_SETTINGS)
@PreAuthorize(Privileges.Has.MANAGE_COMPANIES)
public class CompanySettingsController {

    private final CompanyService companyService;
    private final CompanyAccessService accessService;
    private final LicenceService licenceService;
    private final Messages messages;

    public CompanySettingsController(CompanyService companyService, CompanyAccessService accessService, LicenceService licenceService,
                                     Messages messages) {
        this.companyService = companyService;
        this.accessService = accessService;
        this.licenceService = licenceService;
        this.messages = messages;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute(CommonCodes.Attributes.COMPANY_LIST, companyService.findAll());
        model.addAttribute(CommonCodes.Attributes.USER_COUNTS, companyService.userCounts());
        model.addAttribute(CommonCodes.Attributes.MAX_COMPANIES, licenceService.isRequired() ? licenceService.maxCompanies() : 0);
        return CommonCodes.Views.COMPANY_LIST;
    }

    @GetMapping(Endpoints.COMPANY_SETTINGS_NEW)
    public String newForm(Model model) {
        model.addAttribute(CommonCodes.Attributes.NEW_COMPANY, new NewCompanyForm());
        return formView(model);
    }

    /**
     * Creates the company, opens it to its creator with the profile they have here, and switches to it: its
     * Paramètres page comes next, to complete its details.
     */
    @PostMapping(Endpoints.COMPANY_SETTINGS_CREATE)
    public String create(@Valid @ModelAttribute(CommonCodes.Attributes.NEW_COMPANY) NewCompanyForm form, BindingResult result,
                         @AuthenticationPrincipal AppUserPrincipal principal, Model model, HttpServletRequest request,
                         HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                CompanyAccess current = accessService.require(principal.getId(), principal.getCompanyId());
                Company company = companyService.create(form, principal.getId(), current.getProfile());
                CompanyAccess access = accessService.require(principal.getId(), company.getId());
                SignedInUser.replace(principal.withCompany(access), request, response);
                accessService.rememberLast(principal.getId(), company.getId());
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS,
                    messages.get(MessageProperties.COMPANY_CREATED, company.getName()));
                return Endpoints.REDIRECT + Endpoints.ISSUER;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    @PostMapping(Endpoints.COMPANY_SETTINGS_RENAME)
    public String rename(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirectAttributes) {
        if (name == null || name.isBlank() || name.length() > 255) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.COMPANY_ERROR_NAME));
        } else {
            Company company = companyService.rename(id, name);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.COMPANY_RENAMED, company.getName()));
        }
        return Endpoints.REDIRECT + Endpoints.COMPANY_SETTINGS;
    }

    @PostMapping(Endpoints.COMPANY_SETTINGS_DEACTIVATE)
    public String deactivate(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal,
                             RedirectAttributes redirectAttributes) {
        return setActive(id, false, principal, redirectAttributes);
    }

    @PostMapping(Endpoints.COMPANY_SETTINGS_ACTIVATE)
    public String activate(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal,
                           RedirectAttributes redirectAttributes) {
        return setActive(id, true, principal, redirectAttributes);
    }

    private String setActive(Long id, boolean active, AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        try {
            Company company = companyService.setActive(id, active, principal.getCompanyId());
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(
                active ? MessageProperties.COMPANY_ACTIVATED : MessageProperties.COMPANY_DEACTIVATED, company.getName()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.COMPANY_SETTINGS;
    }

    private String formView(Model model) {
        model.addAttribute(CommonCodes.Attributes.TYPES, CompanyType.values());
        return CommonCodes.Views.COMPANY_FORM;
    }
}
