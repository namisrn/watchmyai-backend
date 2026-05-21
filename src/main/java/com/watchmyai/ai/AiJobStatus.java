package com.watchmyai.ai;

/**
 * Terminal state of an async AI job. Serialises to the lowercase API value via
 * {@link #toApiValue()}.
 */
public enum AiJobStatus {
    PROCESSING, COMPLETED, BLOCKED, FAILED;

    /** Lowercase string sent to clients, e.g. {@code "processing"}. */
    public String toApiValue() {
        return name().toLowerCase();
    }
}
