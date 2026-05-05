package com.watchmyai.subscription;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionSyncRequest(
        @NotBlank
        String productId,

        @NotBlank
        String transactionId,

        @NotBlank
        String originalTransactionId,

        @NotBlank
        String environment,

        @NotBlank
        String signedTransactionInfo
) {
}
