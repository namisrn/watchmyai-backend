package com.watchmyai.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
        String signedTransactionInfo,

        Long expirationDateMilliseconds,

        @Pattern(
                regexp = "|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "appAccountToken must be a UUID"
        )
        String appAccountToken
) {
}
