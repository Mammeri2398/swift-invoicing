package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.config.SecurityProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** The logged-in user's two-factor login: setup with a QR code, recovery codes, turning it off. */
@Controller
@RequestMapping(TwoFactorFilter.SETUP_URL)
@PreAuthorize("isAuthenticated() and !hasAuthority('" + TwoFactorLogin.PENDING_AUTHORITY + "')")
public class TwoFactorController {

    /** The secret being set up, kept in the session until the user confirms it with a code. */
    private static final String SETUP_SECRET = TwoFactorController.class.getName() + ".SECRET";

    private final TwoFactorService twoFactorService;
    private final SessionService sessionService;
    private final List<String> requiredFor;

    public TwoFactorController(TwoFactorService twoFactorService, SessionService sessionService, SecurityProperties properties) {
        this.twoFactorService = twoFactorService;
        this.sessionService = sessionService;
        this.requiredFor = properties.twoFactor().requiredFor();
    }

    /** Status, and the QR code when not set up yet or when moving to a new phone ({@code ?change=true}). */
    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal principal,
                       @RequestParam(name = "change", defaultValue = "false") boolean change,
                       HttpSession session, Model model) {
        boolean enabled = twoFactorService.isEnabled(principal.getId());
        model.addAttribute("enabled", enabled);
        model.addAttribute("required", isRequired(principal));
        if (enabled) {
            model.addAttribute("remainingCodes", twoFactorService.remainingRecoveryCodes(principal.getId()));
        }
        if (!enabled || change) {
            String secret = (String) session.getAttribute(SETUP_SECRET);
            if (secret == null) {
                secret = Totp.newSecret();
                session.setAttribute(SETUP_SECRET, secret);
            }
            model.addAttribute("setup", twoFactorService.setup(principal.getUsername(), secret));
        }
        return "access/account/two-factor";
    }

    @PostMapping("/confirm")
    public String confirm(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                          HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession();
        String secret = (String) session.getAttribute(SETUP_SECRET);
        if (secret == null) {
            return "redirect:" + TwoFactorFilter.SETUP_URL;
        }
        boolean change = twoFactorService.isEnabled(principal.getId());
        try {
            List<String> codes = twoFactorService.enable(principal.getId(), secret, code);
            session.removeAttribute(SETUP_SECRET);
            refreshPrincipal(principal.withTwoFactorEnabled(true), request, response);
            // Sessions opened with the password alone end
            sessionService.expireSessions(principal.getUsername(), session.getId());
            redirectAttributes.addFlashAttribute("recoveryCodes", codes);
            redirectAttributes.addFlashAttribute("success", change
                ? "Double authentification déplacée sur votre nouveau téléphone."
                : "Double authentification activée : le code de l'application vous sera demandé à chaque connexion.");
            return "redirect:" + TwoFactorFilter.SETUP_URL;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + TwoFactorFilter.SETUP_URL + (change ? "?change=true" : "");
        }
    }

    @PostMapping("/recovery-codes")
    public String regenerate(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                             RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("recoveryCodes", twoFactorService.regenerateRecoveryCodes(principal.getId(), code));
            redirectAttributes.addFlashAttribute("success", "Nouveaux codes de secours générés : les anciens ne fonctionnent plus.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + TwoFactorFilter.SETUP_URL;
    }

    @PostMapping("/disable")
    public String disable(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                          HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (isRequired(principal)) {
            redirectAttributes.addFlashAttribute("error", "La double authentification est obligatoire pour votre profil.");
            return "redirect:" + TwoFactorFilter.SETUP_URL;
        }
        try {
            twoFactorService.disable(principal.getId(), code);
            refreshPrincipal(principal.withTwoFactorEnabled(false), request, response);
            redirectAttributes.addFlashAttribute("success", "Double authentification désactivée.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + TwoFactorFilter.SETUP_URL;
    }

    private boolean isRequired(AppUserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a -> requiredFor.contains(a.getAuthority()));
    }

    private static void refreshPrincipal(AppUserPrincipal updated, HttpServletRequest request, HttpServletResponse response) {
        TwoFactorLogin.save(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()), request, response);
    }
}
