package com.swiftlabs.invoicing.licence;

/** A key that is not a valid licence of this publisher; the message is shown to the user. */
public class InvalidLicenceException extends RuntimeException {

    public InvalidLicenceException(String message) {
        super(message);
    }
}
