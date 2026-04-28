package com.watchmyai.quota;

public record QuotaCheckResult(
        PlanType planType,
        boolean requestAllowed,
        int remainingRequests,
        int usedPremiumRequests,
        int monthlyPremiumRequestLimit,
        int monthlyUsagePercent,
        double estimatedMonthlyCostEur,
        double monthlyCostCapEur,
        String throttleState,
        PlanLimits limits
) {
}
