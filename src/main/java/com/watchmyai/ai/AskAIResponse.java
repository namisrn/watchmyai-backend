package com.watchmyai.ai;

import com.watchmyai.quota.PlanType;

import java.math.BigDecimal;

public record AskAIResponse(
        String answer,
        String modelUsed,
        PlanType planType,
        boolean requestAllowed,
        int remainingRequests,
        int monthlyUsagePercent,
        BigDecimal estimatedMonthlyCostEur,
        BigDecimal monthlyCostCapEur,
        String throttleState
) {
}
