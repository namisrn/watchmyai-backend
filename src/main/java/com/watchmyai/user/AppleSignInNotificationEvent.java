package com.watchmyai.user;

import java.time.Instant;

/**
 * Parsed Apple Sign-In server-to-server notification. Apple delivers four event
 * types on the configured server endpoint:
 *
 * <ul>
 *   <li>{@code email-disabled} — the user revoked private-email forwarding</li>
 *   <li>{@code email-enabled} — the user re-enabled private-email forwarding</li>
 *   <li>{@code consent-revoked} — the user revoked Sign in with Apple for this app
 *       in their iCloud settings. We must delete the server-side identity.</li>
 *   <li>{@code account-delete} — the user deleted their Apple ID entirely.
 *       Same treatment as consent-revoked from our side.</li>
 * </ul>
 *
 * Reference: https://developer.apple.com/documentation/sign_in_with_apple/processing_changes_for_sign_in_with_apple_accounts
 */
public record AppleSignInNotificationEvent(
        Type type,
        String subject,
        String email,
        Instant eventTime
) {
    public enum Type {
        EMAIL_DISABLED,
        EMAIL_ENABLED,
        CONSENT_REVOKED,
        ACCOUNT_DELETE,
        UNKNOWN;

        public static Type fromValue(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            return switch (value.trim().toLowerCase()) {
                case "email-disabled" -> EMAIL_DISABLED;
                case "email-enabled" -> EMAIL_ENABLED;
                case "consent-revoked" -> CONSENT_REVOKED;
                case "account-delete" -> ACCOUNT_DELETE;
                default -> UNKNOWN;
            };
        }
    }

    /**
     * "Hard" events mean the user wants their data gone — we must purge the
     * server-side identity. Soft events ({@code email-*}) are informational and
     * only update the cached email address (we keep the account).
     */
    public boolean requiresAccountPurge() {
        return type == Type.CONSENT_REVOKED || type == Type.ACCOUNT_DELETE;
    }
}
