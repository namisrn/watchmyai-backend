package com.watchmyai.quota;

import org.springframework.stereotype.Service;

@Service
public class UsageService {

    private int usedLifetimeRequests = 5;
    private int usedMonthlyRequests = 0;
    private int usedPremiumRequests = 0;
    private double estimatedMonthlyCostEur = 0.002;

    public UsageSnapshot getCurrentUsage() {
        return new UsageSnapshot(
                usedLifetimeRequests,
                usedMonthlyRequests,
                usedPremiumRequests,
                estimatedMonthlyCostEur
        );
    }

    public void recordRequest(PlanType planType, double estimatedRequestCostEur, boolean premiumRequest) {
        if (planType == PlanType.FREE) {
            usedLifetimeRequests++;
        } else {
            usedMonthlyRequests++;
        }

        if (premiumRequest) {
            usedPremiumRequests++;
        }

        estimatedMonthlyCostEur += estimatedRequestCostEur;
    }

    public void resetUsage() {
        usedLifetimeRequests = 5;
        usedMonthlyRequests = 0;
        usedPremiumRequests = 0;
        estimatedMonthlyCostEur = 0.002;
    }

    public void simulateHighCost() {
        estimatedMonthlyCostEur = 99.0;
    }
}