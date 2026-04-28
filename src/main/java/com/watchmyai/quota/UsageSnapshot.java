package com.watchmyai.quota;

public record UsageSnapshot(
        int usedLifetimeRequests,
        int usedMonthlyRequests,
        int usedPremiumRequests,
        double estimatedMonthlyCostEur
) {
}