package com.watchmyai.quota;

import java.math.BigDecimal;

public record QuotaStatusResponse(
        PlanType planType,
        boolean requestAllowed,
        int remainingRequests,
        int usedPremiumRequests,
        int monthlyPremiumRequestLimit,
        int monthlyUsagePercent,
        BigDecimal estimatedMonthlyCostEur,
        BigDecimal monthlyCostCapEur,
        String throttleState
) {

    public static QuotaStatusResponse from(QuotaCheckResult quota) {
        return new QuotaStatusResponse(
                quota.planType(),
                quota.requestAllowed(),
                quota.remainingRequests(),
                quota.usedPremiumRequests(),
                quota.monthlyPremiumRequestLimit(),
                quota.monthlyUsagePercent(),
                quota.estimatedMonthlyCostEur(),
                quota.monthlyCostCapEur(),
                quota.throttleState()
        );
    }
}
