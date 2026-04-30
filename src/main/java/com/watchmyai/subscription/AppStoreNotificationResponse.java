package com.watchmyai.subscription;

public record AppStoreNotificationResponse(
        boolean accepted,
        String verificationSource
) {
}
