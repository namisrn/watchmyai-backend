package com.watchmyai.quota;

import org.springframework.stereotype.Service;

@Service
public class QuotaService {

    private final PlanConfigService planConfigService;
    private final UsageService usageService;

    public QuotaService(
            PlanConfigService planConfigService,
            UsageService usageService
    ) {
        this.planConfigService = planConfigService;
        this.usageService = usageService;
    }

    public QuotaCheckResult checkQuota(PlanType planType) {
        PlanLimits limits = planConfigService.getLimits(planType);
        UsageSnapshot usage = usageService.getCurrentUsage();

        int usedRequests = planType == PlanType.FREE
                ? usage.usedLifetimeRequests()
                : usage.usedMonthlyRequests();

        int requestLimit = planType == PlanType.FREE
                ? limits.lifetimeRequestLimit()
                : limits.monthlyRequestLimit();

        int remainingRequests = Math.max(requestLimit - usedRequests, 0);

        boolean requestAllowed = remainingRequests > 0
                && usage.estimatedMonthlyCostEur() < limits.monthlyCostCapEur();

        int monthlyUsagePercent = requestLimit == 0
                ? 0
                : Math.min((int) Math.round((usedRequests * 100.0) / requestLimit), 100);

        String throttleState = determineThrottleState(monthlyUsagePercent, requestAllowed);

        return new QuotaCheckResult(
                planType,
                requestAllowed,
                remainingRequests,
                usage.usedPremiumRequests(),
                limits.monthlyPremiumRequestLimit(),
                monthlyUsagePercent,
                roundCost(usage.estimatedMonthlyCostEur()),
                roundCost(limits.monthlyCostCapEur()),
                throttleState,
                limits
        );
    }

    public void resetUsage() {
        usageService.resetUsage();
    }

    public void simulateHighCost() {
        usageService.simulateHighCost();
    }

    private String determineThrottleState(int monthlyUsagePercent, boolean requestAllowed) {
        if (!requestAllowed) {
            return "capped";
        }

        if (monthlyUsagePercent >= 90) {
            return "restricted";
        }

        if (monthlyUsagePercent >= 70) {
            return "careful";
        }

        return "normal";
    }
    private double roundCost(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}