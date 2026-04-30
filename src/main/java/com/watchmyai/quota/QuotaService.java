package com.watchmyai.quota;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

        int lifetimeRemainingRequests = planType == PlanType.FREE
                ? Math.max(limits.lifetimeRequestLimit() - usage.usedLifetimeRequests(), 0)
                : Integer.MAX_VALUE;
        int dailyRemainingRequests = Math.max(limits.dailyRequestLimit() - usage.usedDailyRequests(), 0);
        int monthlyRemainingRequests = Math.max(limits.monthlyRequestLimit() - usage.usedMonthlyRequests(), 0);
        int remainingRequests = Math.min(
                lifetimeRemainingRequests,
                Math.min(dailyRemainingRequests, monthlyRemainingRequests)
        );

        boolean requestAllowed = remainingRequests > 0
                && usage.estimatedMonthlyCostEur().compareTo(limits.monthlyCostCapEur()) < 0;

        int dailyUsagePercent = usagePercent(usage.usedDailyRequests(), limits.dailyRequestLimit());
        int monthlyUsagePercent = usagePercent(usage.usedMonthlyRequests(), limits.monthlyRequestLimit());

        String throttleState = determineThrottleState(Math.max(dailyUsagePercent, monthlyUsagePercent), requestAllowed);

        return new QuotaCheckResult(
                planType,
                requestAllowed,
                remainingRequests,
                dailyRemainingRequests,
                limits.dailyRequestLimit(),
                dailyUsagePercent,
                monthlyRemainingRequests,
                limits.monthlyRequestLimit(),
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

    private int usagePercent(int usedRequests, int requestLimit) {
        return requestLimit == 0
                ? 0
                : Math.min((int) Math.round((usedRequests * 100.0) / requestLimit), 100);
    }

    private BigDecimal roundCost(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
