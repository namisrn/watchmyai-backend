package com.watchmyai.user;

/**
 * Apple Sign-In server-to-server notification request body.
 * Apple POSTs {@code { "payload": "<signed JWT>" }} per
 * https://developer.apple.com/documentation/sign_in_with_apple/processing_changes_for_sign_in_with_apple_accounts
 */
public record AppleSignInNotificationRequest(String payload) {
}
