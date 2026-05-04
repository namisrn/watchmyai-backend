package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;

import java.time.Instant;

public record SubscriptionStatusResponse(
        PlanType planType,
        String productId,
        boolean verified,
        String verificationSource,
        String transactionId,
        String originalTransactionId,
        String environment,
        Instant expiresAt,
        Instant revokedAt,
        String entitlementStatus
) {
    public SubscriptionStatusResponse(PlanType planType, String productId, boolean verified) {
        this(
                planType,
                productId,
                verified,
                verified ? "free_fallback" : "none",
                null,
                null,
                null,
                null,
                null,
                verified ? "ACTIVE" : "UNKNOWN"
        );
    }

    public SubscriptionStatusResponse(
            PlanType planType,
            String productId,
            boolean verified,
            String verificationSource,
            String transactionId,
            String originalTransactionId,
            String environment
    ) {
        this(
                planType,
                productId,
                verified,
                verificationSource,
                transactionId,
                originalTransactionId,
                environment,
                null,
                null,
                verified ? "ACTIVE" : "UNKNOWN"
        );
    }
}
