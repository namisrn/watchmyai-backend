package com.watchmyai.ai;

import com.watchmyai.quota.PlanType;

import java.math.BigDecimal;

public record AskAIResponse(
        String status,
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
    public static final String STATUS_PROCESSING = AiJobStatus.PROCESSING.toApiValue();
    public static final String STATUS_COMPLETED  = AiJobStatus.COMPLETED.toApiValue();
    public static final String STATUS_BLOCKED    = AiJobStatus.BLOCKED.toApiValue();
    public static final String STATUS_FAILED     = AiJobStatus.FAILED.toApiValue();
}
