package com.watchmyai.ai;

import com.watchmyai.quota.PlanType;

public record AskAIResponse(
        String answer,
        String modelUsed,
        PlanType planType,
        boolean requestAllowed,
        int remainingRequests,
        int monthlyUsagePercent,
        double estimatedMonthlyCostEur,
        double monthlyCostCapEur,
        String throttleState
) {
}