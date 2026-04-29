package com.watchmyai.quota;

import java.math.BigDecimal;

public record UsageSnapshot(
        int usedLifetimeRequests,
        int usedMonthlyRequests,
        int usedPremiumRequests,
        BigDecimal estimatedMonthlyCostEur
) {
}
