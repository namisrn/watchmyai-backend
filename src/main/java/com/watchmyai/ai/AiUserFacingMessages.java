package com.watchmyai.ai;

/**
 * Centralised user-facing message constants returned by the AI job lifecycle.
 *
 * <p>Why this exists: previous revisions mixed German (e.g. "Die Anfrage konnte nicht
 * verarbeitet werden.") and English strings inline across {@link AiService},
 * {@link AiRequestLogEntity} and {@link OpenAiClient}. The frontend renders these
 * strings verbatim in the conversation error chip, so a Japanese, French or English
 * user would see German pop up at random. Pulling the strings into one place and
 * normalising them to English makes the contract consistent and matches the
 * `sourceLanguage: "en"` declared in both targets' `Localizable.xcstrings`.
 *
 * <p>The frontend already pattern-matches well-known backend messages in
 * `WatchMyAIKit/Sources/WatchMyAIKit/APIClient.swift > decodeErrorMessage` and maps
 * them to localised strings. Future work: replace these free-text fields with a
 * structured `messageCode` enum so the frontend can localise without substring
 * matching. For now: English is the single source.
 */
public final class AiUserFacingMessages {

    private AiUserFacingMessages() {}

    /** Returned alongside `status=processing` while the job is in flight. */
    public static final String PROCESSING = "Your request is being processed.";

    /** Returned in the catch block when an unrecognised exception interrupts the AI call. */
    public static final String PROCESSING_FAILED = "Your request could not be processed.";

    /** Returned alongside `status=blocked` when the quota gate rejects the request. */
    public static final String LIMIT_REACHED =
            "Your current limit has been reached. Please upgrade or try again later.";

    /**
     * Surfaced when the OpenAI circuit breaker is open after repeated provider failures.
     * Maps to HTTP 503 via {@link OpenAiClientException}.
     */
    public static final String AI_PROVIDER_UNAVAILABLE =
            "The AI provider is temporarily unavailable. Please try again shortly.";

    // ---- Dev / mock paths (never reach prod under `ProductionSecretsValidator`) ----

    /** Dev safety: thrown when the OpenAI key is missing AND mock mode is off. */
    public static final String MISSING_API_KEY = "OPENAI_API_KEY is not configured on the backend.";

    /** Prefix for synthetic mock responses returned by the dev/test mock provider path. */
    public static final String MOCK_ANSWER_PREFIX = "Mock response from OpenAiClient.";
}
