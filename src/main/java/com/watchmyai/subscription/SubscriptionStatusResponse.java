package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;

public record SubscriptionStatusResponse(
        PlanType planType,
        String productId,
        boolean verified
) {
}
