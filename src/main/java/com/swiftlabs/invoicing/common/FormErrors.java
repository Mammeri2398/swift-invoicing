package com.swiftlabs.invoicing.common;

import org.springframework.validation.BindingResult;

/** Puts a business error into a form's binding result, next to its field when it has one. */
public final class FormErrors {

    private static final String CODE = "business";

    private FormErrors() {
    }

    public static void reject(BindingResult result, BusinessException ex) {
        if (ex.getField() != null && result.getFieldType(ex.getField()) != null) {
            result.rejectValue(ex.getField(), CODE, ex.getMessage());
        } else {
            result.reject(CODE, ex.getMessage());
        }
    }
}
