package com.swiftlabs.invoicing.common;

/**
 * A business rule refused the operation. It carries a message key (and its arguments): the user
 * reads it in their language (see {@link Messages#of}); {@link #getMessage()} is the French text,
 * for logs and tests. When the rule is about one form field (e.g. "username already used"),
 * {@link #getField()} names it so the error can be displayed next to that field.
 */
public class BusinessException extends RuntimeException {

    private final String field;
    private final String code;
    private final transient Object[] args;

    /** An error about the whole operation: {@code new BusinessException("client.error.inUse", name)}. */
    public BusinessException(String code, Object... args) {
        this(null, code, args);
    }

    protected BusinessException(String field, String code, Object[] args) {
        super(Messages.french(code, args));
        this.field = field;
        this.code = code;
        this.args = args == null ? new Object[0] : args.clone();
    }

    /** An error shown next to one form field. */
    public static BusinessException onField(String field, String code, Object... args) {
        return new BusinessException(field, code, args);
    }

    /** The form field the error belongs to, or null for an error about the whole form. */
    public String getField() {
        return field;
    }

    /** The message key, in the i18n bundles. */
    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args.clone();
    }
}
