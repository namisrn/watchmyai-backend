package com.watchmyai.quota;

/**
 * Throttle state derived from the user's quota consumption.
 * Serialises to the lowercase API value via {@link #toApiValue()}.
 */
public enum QuotaState {
    NORMAL, CAREFUL, RESTRICTED, CAPPED;

    /** Lowercase string sent to clients, e.g. {@code "normal"}. */
    public String toApiValue() {
        return name().toLowerCase();
    }
}
