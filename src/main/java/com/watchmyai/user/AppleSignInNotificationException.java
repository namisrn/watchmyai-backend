package com.watchmyai.user;

/**
 * Thrown when an Apple Sign-In server-to-server notification fails verification
 * (signature, claims, payload shape). Distinct from {@link AuthenticationRequiredException}
 * because the caller is Apple, not a user — we map this to HTTP 400 rather than 401.
 */
public class AppleSignInNotificationException extends RuntimeException {

    public AppleSignInNotificationException(String message) {
        super(message);
    }

    public AppleSignInNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
