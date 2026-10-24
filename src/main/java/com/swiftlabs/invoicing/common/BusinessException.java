package com.swiftlabs.invoicing.common;

/**
 * A business rule refused the operation. The message is shown to the user; when the rule is
 * about one form field (e.g. "username already used"), {@link #getField()} names it so the
 * error can be displayed next to that field.
 */
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The form field the error belongs to, or null for an error about the whole form. */
    public String getField() {
        return field;
    }
}
