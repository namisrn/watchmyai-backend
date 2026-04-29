package com.watchmyai.quota;

import java.math.BigDecimal;

public record QuotaCheckResult(
        PlanType planType,
        boolean requestAllowed,
        int remainingRequests,
        int usedPremiumRequests,
        int monthlyPremiumRequestLimit,
        int monthlyUsagePercent,
        BigDecimal estimatedMonthlyCostEur,
        BigDecimal monthlyCostCapEur,
        String throttleState,
        PlanLimits limits
) {
}
