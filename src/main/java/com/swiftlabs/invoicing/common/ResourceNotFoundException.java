package com.swiftlabs.invoicing.common;

/** The requested record does not exist (answered with HTTP 404). */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String code, Object... args) {
        super(code, args);
    }
}
