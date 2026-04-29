package com.watchmyai.quota;

import java.math.BigDecimal;

public record QuotaCheckResult(
        PlanType planType,
        boolean requestAllowed,
        int remainingRequests,
        int dailyRemainingRequests,
        int dailyRequestLimit,
        int dailyUsagePercent,
        int monthlyRemainingRequests,
        int monthlyRequestLimit,
        int usedPremiumRequests,
        int monthlyPremiumRequestLimit,
        int monthlyUsagePercent,
        BigDecimal estimatedMonthlyCostEur,
        BigDecimal monthlyCostCapEur,
        String throttleState,
        PlanLimits limits
) {
}
