package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.audit.AuditKind;
import com.swiftlabs.invoicing.audit.AuditRecorder;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;

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
@RequestMapping(Endpoints.LOGIN_VERIFY)
@PreAuthorize("hasAuthority('" + TwoFactorLogin.PENDING_AUTHORITY + "')")
public class TwoFactorLoginController {

    private final TwoFactorService twoFactorService;
    private final TwoFactorLoginSuccessHandler successHandler;
    private final Messages messages;
    private final AuditRecorder auditRecorder;

    public TwoFactorLoginController(TwoFactorService twoFactorService, TwoFactorLoginSuccessHandler successHandler,
                                    Messages messages, AuditRecorder auditRecorder) {
        this.twoFactorService = twoFactorService;
        this.messages = messages;
        this.successHandler = successHandler;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping
    public String form() {
        return CommonCodes.Views.ACCESS_TWO_FACTOR_VERIFY;
    }

    @PostMapping
    public String verify(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String code,
                         HttpServletRequest request, HttpServletResponse response,
                         RedirectAttributes redirectAttributes) throws IOException, ServletException {
        TwoFactorService.Check check = twoFactorService.verifyLogin(principal.getId(), code);
        switch (check) {
            case ACCEPTED, RECOVERY_CODE_USED -> {
                Authentication full = TwoFactorLogin.complete(request, response);
                auditRecorder.record(AuditKind.SIGN_IN, AuditRecorder.Actor.of(full), request.getRemoteAddr());
                if (check == TwoFactorService.Check.RECOVERY_CODE_USED) {
                    warnAboutRecoveryCodes(principal, request, response);
                }
                successHandler.continueTo(request, response, full);
                return null;
            }
            case LOCKED -> {
                auditRecorder.record(AuditKind.SIGN_IN_LOCKED, AuditRecorder.Actor.of(principal), request.getRemoteAddr());
                // Same answer as for a wrong password: the login page explains the lockout
                SecurityContextHolder.clearContext();
                request.getSession().invalidate();
                return Endpoints.REDIRECT + Endpoints.LOGIN + "?error";
            }
            default -> {
                auditRecorder.record(AuditKind.SIGN_IN_FAILED, AuditRecorder.Actor.of(principal), request.getRemoteAddr());
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.TWO_FACTOR_ERROR_WRONG_CODE));
                return Endpoints.REDIRECT + Endpoints.LOGIN_VERIFY;
            }
        }
    }

    /** Shown on the next page: the login's own redirect does not go through RedirectAttributes. */
    private void warnAboutRecoveryCodes(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        long left = twoFactorService.remainingRecoveryCodes(principal.getId());
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        // In the user's own language: this request was still in the one of the login page
        flash.put("message", principal.getLanguage() != null
            ? messages.get(principal.getLanguage(), MessageProperties.TWO_FACTOR_RECOVERY_CODE_USED, left)
            : messages.get(MessageProperties.TWO_FACTOR_RECOVERY_CODE_USED, left));
        RequestContextUtils.getFlashMapManager(request).saveOutputFlashMap(flash, request, response);
    }
}
