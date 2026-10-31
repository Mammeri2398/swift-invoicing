package com.swiftlabs.invoicing.audit;

import com.swiftlabs.invoicing.access.twofactor.TwoFactorLogin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * The end of a session in the activity journal: signed out, or ended without (inactivity, server stopped). A sign-out
 * ends the session too: it is marked first, to be journaled once.
 * Registered on the logout in SecurityConfig, where it runs before the session is closed.
 */
@Component
public class AuditSessionEvents implements LogoutHandler {

    private static final String SIGNED_OUT = AuditSessionEvents.class.getName() + ".SIGNED_OUT";

    private final AuditRecorder auditRecorder;

    public AuditSessionEvents(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        AuditRecorder.Actor actor = AuditRecorder.Actor.of(authentication);
        if (actor.isAnonymous()) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SIGNED_OUT, Boolean.TRUE);
        }
        auditRecorder.record(AuditKind.SIGN_OUT, actor, request.getRemoteAddr());
    }

    /** Not for a sign-in left at the two-factor code: it never started. */
    @EventListener
    public void onSessionEnded(HttpSessionDestroyedEvent event) {
        try {
            if (event.getSession().getAttribute(SIGNED_OUT) != null) {
                return;
            }
        } catch (IllegalStateException alreadyClosed) {
            return;
        }
        for (SecurityContext context : event.getSecurityContexts()) {
            Authentication authentication = context.getAuthentication();
            AuditRecorder.Actor actor = AuditRecorder.Actor.of(authentication);
            if (!actor.isAnonymous() && !TwoFactorLogin.isPending(authentication)) {
                auditRecorder.record(AuditKind.SESSION_ENDED, actor, null);
            }
        }
    }
}
