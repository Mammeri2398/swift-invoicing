package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;

/** The password reset link is unknown, already used or expired. */
public class InvalidResetTokenException extends RuntimeException {

    /** Message key shown to the user. */
    public static final String CODE = MessageProperties.AUTH_RESET_INVALID_LINK;

    public InvalidResetTokenException() {
        super(Messages.french(CODE));
    }
}
