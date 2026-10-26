package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final PasswordPolicy passwordPolicy;
    private final Messages messages;
    private final long linkValidityMinutes;

    public PasswordResetController(PasswordResetService passwordResetService, PasswordPolicy passwordPolicy, Messages messages,
                                   SecurityProperties properties) {
        this.passwordResetService = passwordResetService;
        this.passwordPolicy = passwordPolicy;
        this.messages = messages;
        this.linkValidityMinutes = properties.resetTokenValidity().toMinutes();
    }

    @GetMapping(Endpoints.FORGOT_PASSWORD)
    public String forgotForm(Model model) {
        model.addAttribute(CommonCodes.Attributes.LINK_VALIDITY_MINUTES, linkValidityMinutes);
        return CommonCodes.Views.ACCESS_FORGOT_PASSWORD;
    }

    @PostMapping(Endpoints.FORGOT_PASSWORD_SEND)
    public String sendReset(@RequestParam String email, RedirectAttributes redirectAttributes) {
        passwordResetService.requestReset(email);
        // Same answer whether the email exists or not
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.MESSAGE, messages.get(MessageProperties.AUTH_FORGOT_SENT));
        return Endpoints.REDIRECT + Endpoints.LOGIN;
    }

    @GetMapping(Endpoints.RESET_PASSWORD)
    public String resetForm(@RequestParam String token, Model model, RedirectAttributes redirectAttributes) {
        if (!passwordResetService.isValid(token)) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(InvalidResetTokenException.CODE));
            return Endpoints.REDIRECT + Endpoints.LOGIN;
        }
        model.addAttribute(CommonCodes.Attributes.TOKEN, token);
        model.addAttribute(CommonCodes.Attributes.PASSWORD_RULES, passwordPolicy.describe());
        return CommonCodes.Views.ACCESS_RESET_PASSWORD;
    }

    @PostMapping(Endpoints.RESET_PASSWORD_SAVE)
    public String saveReset(@RequestParam String token,
                            @RequestParam String password,
                            @RequestParam String confirmPassword,
                            RedirectAttributes redirectAttributes) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.AUTH_RESET_MISMATCH));
            return Endpoints.REDIRECT + Endpoints.RESET_PASSWORD + "?token=" + token;
        }
        try {
            passwordResetService.resetPassword(token, password);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.AUTH_RESET_DONE));
            return Endpoints.REDIRECT + Endpoints.LOGIN;
        } catch (InvalidResetTokenException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(InvalidResetTokenException.CODE));
            return Endpoints.REDIRECT + Endpoints.LOGIN;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.RESET_PASSWORD + "?token=" + token;
        }
    }
}
