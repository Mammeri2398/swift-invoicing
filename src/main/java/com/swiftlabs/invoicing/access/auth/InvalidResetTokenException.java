package com.swiftlabs.invoicing.access.auth;

/** The password reset link is unknown, already used or expired. */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("Lien de réinitialisation invalide ou expiré.");
    }
}
