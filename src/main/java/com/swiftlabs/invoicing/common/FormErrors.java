package com.swiftlabs.invoicing.common;

import org.springframework.validation.BindingResult;

/**
 * Puts a business error into a form's binding result, next to its field when it has one. The page
 * resolves the message key in the user's language.
 */
public final class FormErrors {

    private FormErrors() {
    }

    public static void reject(BindingResult result, BusinessException ex) {
        if (ex.getField() != null && result.getFieldType(ex.getField()) != null) {
            result.rejectValue(ex.getField(), ex.getCode(), ex.getArgs(), ex.getMessage());
        } else {
            result.reject(ex.getCode(), ex.getArgs(), ex.getMessage());
        }
    }
}
