package com.swiftlabs.invoicing.access.auth;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/** Ends a user's open sessions, e.g. after a password change or when the account is disabled. */
@Service
public class SessionService {

    private final SessionRegistry sessionRegistry;

    public SessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /** Expires every open session, e.g. after a restore replaced the users. */
    public void expireAll() {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                session.expireNow();
            }
        }
    }

    /** Expires every session of the user except {@code keepSessionId} (may be null). */
    public void expireSessions(String username, String keepSessionId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserPrincipal user && user.getUsername().equals(username)) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    if (!session.getSessionId().equals(keepSessionId)) {
                        session.expireNow();
                    }
                }
            }
        }
    }
}
