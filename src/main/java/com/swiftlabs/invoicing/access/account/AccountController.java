package com.swiftlabs.invoicing.access.account;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.access.auth.SignedInUser;
import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The logged-in user's own account: personal information, password change (also forced after a
 * temporary password, on its own page) and a link to the two-factor login settings.
 */
@Controller
@RequestMapping(Endpoints.ACCOUNT)
public class AccountController {

    private final UserService userService;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final Messages messages;

    public AccountController(UserService userService, PasswordPolicy passwordPolicy, SessionService sessionService,
                             Messages messages) {
        this.userService = userService;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.messages = messages;
    }

    /** {@code ?password} opens the password form (after an error in it). */
    @GetMapping
    public String account(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(name = "password", defaultValue = "false") boolean openPassword, Model model) {
        model.addAttribute(CommonCodes.Attributes.ACCOUNT, AccountForm.from(user(principal)));
        model.addAttribute(CommonCodes.Attributes.OPEN_PASSWORD, openPassword);
        return accountView(principal, model);
    }

    @PostMapping(Endpoints.ACCOUNT_SAVE)
    public String save(@AuthenticationPrincipal AppUserPrincipal principal,
                       @Valid @ModelAttribute(CommonCodes.Attributes.ACCOUNT) AccountForm form, BindingResult result, Model model,
                       HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                User user = userService.updateOwnDetails(principal.getId(), form.getFirstName(), form.getLastName(), form.getEmail());
                // The header shows the new name straight away
                SignedInUser.replace(principal.withDisplayName(user.getDisplayName()), request, response);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.ACCOUNT_SAVED));
                return Endpoints.REDIRECT + Endpoints.ACCOUNT;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        model.addAttribute(CommonCodes.Attributes.OPEN_PASSWORD, false);
        return accountView(principal, model);
    }

    /** The interface switches to the chosen language at once (it is read from the session's user). */
    @PostMapping(Endpoints.ACCOUNT_LANGUAGE)
    public String changeLanguage(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String language,
                                 HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        Language chosen = Language.of(language);
        userService.updateOwnLanguage(principal.getId(), chosen);
        SignedInUser.replace(principal.withLanguage(chosen), request, response);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(chosen, MessageProperties.ACCOUNT_LANGUAGE_SAVED));
        return Endpoints.REDIRECT + Endpoints.ACCOUNT;
    }

    /** Page of its own for the forced change after a temporary password; otherwise the form sits in "Mon compte". */
    @GetMapping(Endpoints.ACCOUNT_PASSWORD)
    public String passwordForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        if (!principal.isMustChangePassword()) {
            return Endpoints.REDIRECT + Endpoints.ACCOUNT + "?password=true";
        }
        model.addAttribute(CommonCodes.Attributes.PASSWORD_RULES, passwordPolicy.describe());
        return CommonCodes.Views.ACCESS_ACCOUNT_PASSWORD;
    }

    @PostMapping(Endpoints.ACCOUNT_PASSWORD)
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpServletRequest request, HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        boolean forced = principal.isMustChangePassword();
        String formUrl = forced ? Endpoints.REDIRECT + Endpoints.ACCOUNT_PASSWORD_PAGE : Endpoints.REDIRECT + Endpoints.ACCOUNT + "?password=true";
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.ACCOUNT_PASSWORD_MISMATCH));
            return formUrl;
        }
        try {
            userService.changeOwnPassword(principal.getId(), currentPassword, newPassword);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return formUrl;
        }
        SignedInUser.replace(principal.withPasswordChanged(), request, response);
        // New session id, and the user's other sessions (other browsers) are ended
        String sessionId = request.changeSessionId();
        sessionService.expireSessions(principal.getUsername(), sessionId);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.ACCOUNT_PASSWORD_CHANGED));
        return forced ? Endpoints.REDIRECT + Endpoints.HOME : Endpoints.REDIRECT + Endpoints.ACCOUNT;
    }

    private String accountView(AppUserPrincipal principal, Model model) {
        model.addAttribute(CommonCodes.Attributes.USER, user(principal));
        model.addAttribute(CommonCodes.Attributes.PASSWORD_RULES, passwordPolicy.describe());
        model.addAttribute(CommonCodes.Attributes.LANGUAGES, Language.values());
        return CommonCodes.Views.ACCESS_ACCOUNT_INDEX;
    }

    private User user(AppUserPrincipal principal) {
        return userService.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException(MessageProperties.USER_ERROR_NOT_FOUND));
    }
}
