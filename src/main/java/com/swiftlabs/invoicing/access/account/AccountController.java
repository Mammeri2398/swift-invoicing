package com.swiftlabs.invoicing.access.account;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
@RequestMapping("/account")
public class AccountController {

    private final UserService userService;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final Messages messages;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

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
        model.addAttribute("account", AccountForm.from(user(principal)));
        model.addAttribute("openPassword", openPassword);
        return accountView(principal, model);
    }

    @PostMapping("/save")
    public String save(@AuthenticationPrincipal AppUserPrincipal principal,
                       @Valid @ModelAttribute("account") AccountForm form, BindingResult result, Model model,
                       HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                User user = userService.updateOwnDetails(principal.getId(), form.getFirstName(), form.getLastName(), form.getEmail());
                // The header shows the new name straight away
                refreshAuthentication(principal.withDisplayName(user.getDisplayName()), request, response);
                redirectAttributes.addFlashAttribute("success", messages.get("account.saved"));
                return "redirect:/account";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        model.addAttribute("openPassword", false);
        return accountView(principal, model);
    }

    /** The interface switches to the chosen language at once (it is read from the session's user). */
    @PostMapping("/language")
    public String changeLanguage(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String language,
                                 HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        Language chosen = Language.of(language);
        userService.updateOwnLanguage(principal.getId(), chosen);
        refreshAuthentication(principal.withLanguage(chosen), request, response);
        redirectAttributes.addFlashAttribute("success", messages.get(chosen, "account.language.saved"));
        return "redirect:/account";
    }

    /** Page of its own for the forced change after a temporary password; otherwise the form sits in "Mon compte". */
    @GetMapping("/password")
    public String passwordForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        if (!principal.isMustChangePassword()) {
            return "redirect:/account?password=true";
        }
        model.addAttribute("passwordRules", passwordPolicy.describe());
        return "access/account/password";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpServletRequest request, HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        boolean forced = principal.isMustChangePassword();
        String formUrl = forced ? "redirect:/account/password" : "redirect:/account?password=true";
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", messages.get("account.password.mismatch"));
            return formUrl;
        }
        try {
            userService.changeOwnPassword(principal.getId(), currentPassword, newPassword);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
            return formUrl;
        }
        refreshAuthentication(principal.withPasswordChanged(), request, response);
        // New session id, and the user's other sessions (other browsers) are ended
        String sessionId = request.changeSessionId();
        sessionService.expireSessions(principal.getUsername(), sessionId);
        redirectAttributes.addFlashAttribute("success", messages.get("account.password.changed"));
        return forced ? "redirect:/" : "redirect:/account";
    }

    private String accountView(AppUserPrincipal principal, Model model) {
        model.addAttribute("user", user(principal));
        model.addAttribute("passwordRules", passwordPolicy.describe());
        model.addAttribute("languages", Language.values());
        return "access/account/index";
    }

    private User user(AppUserPrincipal principal) {
        return userService.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("user.error.notFound"));
    }

    /** The session keeps an updated principal, without logging in again. */
    private void refreshAuthentication(AppUserPrincipal updated, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
