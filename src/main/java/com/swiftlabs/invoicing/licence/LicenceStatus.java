package com.swiftlabs.invoicing.licence;

/** Where this install stands with its licence, and what to tell the user. */
public record LicenceStatus(State state, Licence licence, long daysLeft, String message) {

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
