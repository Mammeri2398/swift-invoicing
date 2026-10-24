package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;

/** Second login step: the code from the authenticator app, or a recovery code. */
@Controller
@RequestMapping(TwoFactorLogin.VERIFY_URL)
@PreAuthorize("hasAuthority('" + TwoFactorLogin.PENDING_AUTHORITY + "')")
public class TwoFactorLoginController {

    private final TwoFactorService twoFactorService;
    private final TwoFactorLoginSuccessHandler successHandler;

    public TwoFactorLoginController(TwoFactorService twoFactorService, TwoFactorLoginSuccessHandler successHandler) {
        this.twoFactorService = twoFactorService;
        this.successHandler = successHandler;
    }

    @GetMapping
    public String form() {
        return "access/two-factor-verify";
    }

    @PostMapping
    public String verify(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                         HttpServletRequest request, HttpServletResponse response,
                         RedirectAttributes redirectAttributes) throws IOException, ServletException {
        TwoFactorService.Check check = twoFactorService.verifyLogin(principal.getId(), code);
        switch (check) {
            case ACCEPTED, RECOVERY_CODE_USED -> {
                Authentication full = TwoFactorLogin.complete(request, response);
                if (check == TwoFactorService.Check.RECOVERY_CODE_USED) {
                    warnAboutRecoveryCodes(principal.getId(), request, response);
                }
                successHandler.continueTo(request, response, full);
                return null;
            }
            case LOCKED -> {
                // Same answer as for a wrong password: the login page explains the lockout
                SecurityContextHolder.clearContext();
                request.getSession().invalidate();
                return "redirect:/login?error";
            }
            default -> {
                redirectAttributes.addFlashAttribute("error", "Code incorrect.");
                return "redirect:" + TwoFactorLogin.VERIFY_URL;
            }
        }
    }

    /** Shown on the next page: the login's own redirect does not go through RedirectAttributes. */
    private void warnAboutRecoveryCodes(Long userId, HttpServletRequest request, HttpServletResponse response) {
        long left = twoFactorService.remainingRecoveryCodes(userId);
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("message", "Vous vous êtes connecté avec un code de secours. Il vous en reste " + left
            + ". Si vous avez perdu votre téléphone, reconfigurez la double authentification dans votre compte.");
        RequestContextUtils.getFlashMapManager(request).saveOutputFlashMap(flash, request, response);
    }
}
