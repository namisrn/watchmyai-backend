package com.watchmyai.subscription;

import jakarta.validation.constraints.NotBlank;

public record AppStoreNotificationRequest(
        @NotBlank
        String signedPayload
) {
}
