package com.swiftlabs.invoicing.audit;

/** What an entry of the activity journal records. */
public enum AuditKind {
    /** A page shown (GET), or a file downloaded. */
    VIEW,
    /** A change asked for (POST): save, delete, issue, switch company... */
    ACTION,
    SIGN_IN,
    /** Wrong password or two-factor code, or unknown user. */
    SIGN_IN_FAILED,
    /** Refused because the account is locked after too many failures. */
    SIGN_IN_LOCKED,
    SIGN_OUT,
    /** The session ended without signing out: inactivity, or the server stopped. */
    SESSION_ENDED
}
