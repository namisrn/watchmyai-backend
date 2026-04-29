package com.watchmyai.quota;

import java.math.BigDecimal;

public record PlanLimits(
        PlanType planType,
        int lifetimeRequestLimit,
        int monthlyRequestLimit,
        int monthlyPremiumRequestLimit,
        int maxOutputTokens,
        BigDecimal monthlyCostCapEur
) {
}
