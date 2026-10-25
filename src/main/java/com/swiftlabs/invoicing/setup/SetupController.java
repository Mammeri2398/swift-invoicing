package com.swiftlabs.invoicing.setup;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;

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

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("setup", new SetupForm());
        return "setup/setup";
    }

    @PostMapping
    public String complete(@Valid @ModelAttribute("setup") SetupForm form, BindingResult result) {
        if (!result.hasErrors()) {
            try {
                setupService.complete(form);
                return "redirect:/login?setup";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return "setup/setup";
    }
}
