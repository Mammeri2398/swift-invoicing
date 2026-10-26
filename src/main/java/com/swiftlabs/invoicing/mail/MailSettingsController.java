package com.swiftlabs.invoicing.mail;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(Endpoints.MAIL_SETTINGS)
public class MailSettingsController {

    private final MailSettingsService settingsService;
    private final UserService userService;
    private final Messages messages;

    public MailSettingsController(MailSettingsService settingsService, UserService userService, Messages messages) {
        this.settingsService = settingsService;
        this.userService = userService;
        this.messages = messages;
    }

    @GetMapping
    @PreAuthorize(Privileges.Has.VIEW_ISSUER)
    public String form(Model model) {
        model.addAttribute(CommonCodes.Attributes.MAIL, MailSettingsForm.from(settingsService.current()));
        return formView(model);
    }

    @PostMapping
    @PreAuthorize(Privileges.Has.EDIT_ISSUER)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.MAIL) MailSettingsForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                settingsService.save(form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(form.isEnabled()
                    ? MessageProperties.MAIL_SAVED_ENABLED : MessageProperties.MAIL_SAVED_DISABLED));
                return Endpoints.REDIRECT + Endpoints.MAIL_SETTINGS;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    /** Sends a test message to the logged-in user's own address. */
    @PostMapping(Endpoints.MAIL_SETTINGS_TEST)
    @PreAuthorize(Privileges.Has.EDIT_ISSUER)
    public String test(@AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        String to = userService.findById(principal.getId()).map(user -> user.getEmail()).orElse(null);
        try {
            settingsService.sendTest(to);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.MAIL_TEST_SENT, to));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.MAIL_SETTINGS;
    }

    private String formView(Model model) {
        model.addAttribute(CommonCodes.Attributes.HAS_PASSWORD, settingsService.current().getPassword() != null);
        model.addAttribute(CommonCodes.Attributes.SECURITIES, MailSecurity.values());
        return CommonCodes.Views.MAIL_SETTINGS;
    }
}
