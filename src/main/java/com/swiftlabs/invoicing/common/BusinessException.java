package com.swiftlabs.invoicing.common;

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
