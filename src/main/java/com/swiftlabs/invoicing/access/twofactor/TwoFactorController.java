package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
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
@RequestMapping(Endpoints.TWO_FACTOR)
@PreAuthorize("isAuthenticated() and !hasAuthority('" + TwoFactorLogin.PENDING_AUTHORITY + "')")
public class TwoFactorController {

    /** The secret being set up, kept in the session until the user confirms it with a code. */
    private static final String SETUP_SECRET = TwoFactorController.class.getName() + ".SECRET";

    private final TwoFactorService twoFactorService;
    private final SessionService sessionService;
    private final List<String> requiredFor;
    private final Messages messages;

    public TwoFactorController(TwoFactorService twoFactorService, SessionService sessionService, SecurityProperties properties,
                               Messages messages) {
        this.twoFactorService = twoFactorService;
        this.messages = messages;
        this.sessionService = sessionService;
        this.requiredFor = properties.twoFactor().requiredFor();
    }

    /** Status, and the QR code when not set up yet or when moving to a new phone ({@code ?change=true}). */
    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal principal,
                       @RequestParam(name = "change", defaultValue = "false") boolean change,
                       HttpSession session, Model model) {
        boolean enabled = twoFactorService.isEnabled(principal.getId());
        model.addAttribute(CommonCodes.Attributes.ENABLED, enabled);
        model.addAttribute(CommonCodes.Attributes.REQUIRED, isRequired(principal));
        if (enabled) {
            model.addAttribute(CommonCodes.Attributes.REMAINING_CODES, twoFactorService.remainingRecoveryCodes(principal.getId()));
        }
        if (!enabled || change) {
            String secret = (String) session.getAttribute(SETUP_SECRET);
            if (secret == null) {
                secret = Totp.newSecret();
                session.setAttribute(SETUP_SECRET, secret);
            }
            model.addAttribute(CommonCodes.Attributes.SETUP, twoFactorService.setup(principal.getUsername(), secret));
        }
        return CommonCodes.Views.ACCESS_ACCOUNT_TWO_FACTOR;
    }

    @PostMapping(Endpoints.TWO_FACTOR_CONFIRM)
    public String confirm(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                          @RequestParam(required = false) String currentPassword, HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession();
        String secret = (String) session.getAttribute(SETUP_SECRET);
        if (secret == null) {
            return Endpoints.REDIRECT + Endpoints.TWO_FACTOR;
        }
        boolean change = twoFactorService.isEnabled(principal.getId());
        try {
            List<String> codes = twoFactorService.enable(principal.getId(), secret, code, currentPassword);
            session.removeAttribute(SETUP_SECRET);
            refreshPrincipal(principal.withTwoFactorEnabled(true), request, response);
            // Sessions opened with the password alone end
            sessionService.expireSessions(principal.getUsername(), session.getId());
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.RECOVERY_CODES, codes);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS,
                messages.get(change ? MessageProperties.TWO_FACTOR_MOVED : MessageProperties.TWO_FACTOR_ENABLED));
            return Endpoints.REDIRECT + Endpoints.TWO_FACTOR;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.TWO_FACTOR + (change ? "?change=true" : "");
        }
    }

    @PostMapping(Endpoints.TWO_FACTOR_RECOVERY_CODES)
    public String regenerate(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                             RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.RECOVERY_CODES,
                twoFactorService.regenerateRecoveryCodes(principal.getId(), code));
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.TWO_FACTOR_CODES_REGENERATED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.TWO_FACTOR;
    }

    @PostMapping(Endpoints.TWO_FACTOR_DISABLE)
    public String disable(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                          HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (isRequired(principal)) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.TWO_FACTOR_REQUIRED_FOR_PROFILE));
            return Endpoints.REDIRECT + Endpoints.TWO_FACTOR;
        }
        try {
            twoFactorService.disable(principal.getId(), code);
            refreshPrincipal(principal.withTwoFactorEnabled(false), request, response);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.TWO_FACTOR_DISABLED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.TWO_FACTOR;
    }

    private boolean isRequired(AppUserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a -> requiredFor.contains(a.getAuthority()));
    }

    private static void refreshPrincipal(AppUserPrincipal updated, HttpServletRequest request, HttpServletResponse response) {
        TwoFactorLogin.save(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()), request, response);
    }
}
