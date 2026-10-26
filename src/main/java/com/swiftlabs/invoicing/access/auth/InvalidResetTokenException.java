package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.common.Messages;

/** The password reset link is unknown, already used or expired. */
public class InvalidResetTokenException extends RuntimeException {

    /** Message key shown to the user. */
    public static final String CODE = "auth.reset.invalidLink";

    public InvalidResetTokenException() {
        super(Messages.french(CODE));
    }
}
