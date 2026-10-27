package com.swiftlabs.invoicing.setup;

import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.company.CompanySchemas;
import com.swiftlabs.invoicing.company.CompanyWork;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The first-run setup page (reachable only while no user exists, see SetupFilter). */
@Controller
@RequestMapping(Endpoints.SETUP)
public class SetupController {

    private final SetupService setupService;
    private final PasswordPolicy passwordPolicy;
    private final CompanyWork companyWork;
    private final Messages messages;

    public SetupController(SetupService setupService, PasswordPolicy passwordPolicy, CompanyWork companyWork, Messages messages) {
        this.setupService = setupService;
        this.passwordPolicy = passwordPolicy;
        this.companyWork = companyWork;
        this.messages = messages;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute(CommonCodes.Attributes.SETUP, new SetupForm());
        return view(model);
    }

    @PostMapping
    public String complete(@Valid @ModelAttribute(CommonCodes.Attributes.SETUP) SetupForm form, BindingResult result, Model model) {
        if (!result.hasErrors()) {
            try {
                // Nobody is signed in yet: the details are those of the first company, named explicitly
                companyWork.inCompany(CompanySchemas.FIRST, () -> setupService.complete(form, messages.current()));
                return Endpoints.REDIRECT + Endpoints.LOGIN + "?setup";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return view(model);
    }

    private String view(Model model) {
        model.addAttribute(CommonCodes.Attributes.PASSWORD_RULES, passwordPolicy.describe());
        return CommonCodes.Views.SETUP_SETUP;
    }
}
