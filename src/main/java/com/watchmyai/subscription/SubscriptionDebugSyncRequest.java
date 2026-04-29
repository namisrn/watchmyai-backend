package com.watchmyai.subscription;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionDebugSyncRequest(
        @NotBlank
        String productId
) {
}
