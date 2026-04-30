package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;

public record SubscriptionStatusResponse(
        PlanType planType,
        String productId,
        boolean verified,
        String verificationSource,
        String transactionId,
        String originalTransactionId,
        String environment
) {
    public SubscriptionStatusResponse(PlanType planType, String productId, boolean verified) {
        this(
                planType,
                productId,
                verified,
                verified ? "free_fallback" : "none",
                null,
                null,
                null
        );
    }
}
