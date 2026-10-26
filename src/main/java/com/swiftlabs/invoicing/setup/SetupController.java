package com.swiftlabs.invoicing.setup;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Messages;

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
@RequestMapping(SetupFilter.SETUP_URL)
public class SetupController {

    private final SetupService setupService;
    private final PasswordPolicy passwordPolicy;
    private final Messages messages;

    public SetupController(SetupService setupService, PasswordPolicy passwordPolicy, Messages messages) {
        this.setupService = setupService;
        this.passwordPolicy = passwordPolicy;
        this.messages = messages;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("setup", new SetupForm());
        return view(model);
    }

    @PostMapping
    public String complete(@Valid @ModelAttribute("setup") SetupForm form, BindingResult result, Model model) {
        if (!result.hasErrors()) {
            try {
                setupService.complete(form, messages.current());
                return "redirect:/login?setup";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return view(model);
    }

    private String view(Model model) {
        model.addAttribute("passwordRules", passwordPolicy.describe());
        return "setup/setup";
    }
}
