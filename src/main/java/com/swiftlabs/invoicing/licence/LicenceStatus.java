package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.common.Messages;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * Where this install stands with its licence, and what to tell the user: a message key
 * (licence.status.*, licence.error.*) and its arguments, read in each viewer's language.
 */
public record LicenceStatus(State state, Licence licence, long daysLeft, String messageCode, Object[] messageArgs) {

    private static volatile MessageSource texts;

    public LicenceStatus(State state, Licence licence, long daysLeft) {
        this(state, licence, daysLeft, null, new Object[0]);
    }

    public LicenceStatus {
        messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    public enum State {
        /** Licensing is off (development, hosted deployment). */
        NOT_REQUIRED,
        TRIAL,
        VALID,
        /** Valid, expiry date close. */
        EXPIRING,
        /** Expiry date passed, still fully usable for a few days. */
        GRACE,
        /** Read-only from here. */
        EXPIRED,
        TRIAL_OVER,
        /** A key that no longer matches this install (another company's NIF). */
        INVALID
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs.clone();
    }

    /**
     * What to tell the user, in the current request's language (French outside a request), or null.
     * The banner of every page (layout/fragments.html) and the licence page show it.
     */
    public String message() {
        if (messageCode == null) {
            return null;
        }
        Locale locale = LocaleContextHolder.getLocaleContext() != null ? LocaleContextHolder.getLocale() : Locale.FRENCH;
        if (texts == null) {
            texts = Messages.newMessageSource();
        }
        return texts.getMessage(messageCode, messageArgs, messageCode, locale);
    }

    /** Same as {@link #message()}, for templates ({@code ${licence.message}}). */
    public String getMessage() {
        return message();
    }

    /** Data stays viewable and exportable, but nothing can be created or changed. */
    public boolean isReadOnly() {
        return state == State.EXPIRED || state == State.TRIAL_OVER || state == State.INVALID;
    }

    /** Worth a banner on every page. */
    public boolean isNoticeShown() {
        return state != State.NOT_REQUIRED && state != State.VALID;
    }

    public boolean isUrgent() {
        return state == State.GRACE || isReadOnly();
    }
}
