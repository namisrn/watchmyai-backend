package com.watchmyai.quota;

public record PlanLimits(
        PlanType planType,
        int lifetimeRequestLimit,
        int monthlyRequestLimit,
        int monthlyPremiumRequestLimit,
        int maxOutputTokens,
        double monthlyCostCapEur
) {
}
