package com.swiftlabs.invoicing.access.account;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.common.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The logged-in user's own account: password change (also forced after a temporary password). */
@Controller
@RequestMapping("/account/password")
public class AccountController {

    private final UserService userService;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AccountController(UserService userService, PasswordPolicy passwordPolicy, SessionService sessionService) {
        this.userService = userService;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("forced", principal.isMustChangePassword());
        model.addAttribute("passwordRules", passwordPolicy.describe());
        return "access/account/password";
    }

    @PostMapping
    public String change(@AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam String currentPassword,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         HttpServletRequest request, HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Les deux nouveaux mots de passe ne correspondent pas.");
            return "redirect:/account/password";
        }
        try {
            userService.changeOwnPassword(principal.getId(), currentPassword, newPassword);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/account/password";
        }
        refreshAuthentication(principal, request, response);
        // New session id, and the user's other sessions (other browsers) are ended
        String sessionId = request.changeSessionId();
        sessionService.expireSessions(principal.getUsername(), sessionId);
        redirectAttributes.addFlashAttribute("success", "Mot de passe modifié.");
        return "redirect:/";
    }

    /** The session keeps an updated principal: no forced change any more, no need to log in again. */
    private void refreshAuthentication(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal updated = principal.withPasswordChanged();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
