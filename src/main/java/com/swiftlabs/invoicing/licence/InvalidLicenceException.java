package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.common.BusinessException;

/**
 * A key that is not a valid licence of this publisher. Like every business error it carries a message
 * key (licence.error.*): the user reads it in their language, {@link #getMessage()} is the French text.
 */
public class InvalidLicenceException extends BusinessException {

    public InvalidLicenceException(String code, Object... args) {
        super(code, args);
    }
}
