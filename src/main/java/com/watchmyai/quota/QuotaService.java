package com.watchmyai.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

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
        return evaluateQuota(planType, usageService.getCurrentUsage());
    }

    /**
     * Evaluates quota for an explicit user. Used by the AI worker pool, which runs outside the
     * HTTP request scope and cannot resolve the user from the request context.
     */
    public QuotaCheckResult checkQuota(String userId, PlanType planType) {
        return evaluateQuota(planType, usageService.getUsage(userId, planType));
    }

    private QuotaCheckResult evaluateQuota(PlanType planType, UsageSnapshot usage) {
        PlanLimits limits = planConfigService.getLimits(planType);

        int lifetimeRemainingRequests = limits.lifetimeRequestLimit() > 0
                ? Math.max(limits.lifetimeRequestLimit() - usage.usedLifetimeRequests(), 0)
                : Integer.MAX_VALUE;
        int dailyRemainingRequests = Math.max(limits.dailyRequestLimit() - usage.usedDailyRequests(), 0);
        int monthlyRemainingRequests = Math.max(limits.monthlyRequestLimit() - usage.usedMonthlyRequests(), 0);
        int remainingRequests = Math.min(
                lifetimeRemainingRequests,
                Math.min(dailyRemainingRequests, monthlyRemainingRequests)
        );

        // S3-5 diagnostic: a "fresh" user (used == 0) must never compute as
        // "monthly exhausted". If this fires, the bug is upstream — most likely
        // a plan-limit config returning 0 for the monthly cap or a stale row
        // mid-period-rollover. Logged at WARN so on-call can find it without
        // chasing a user-side support ticket.
        boolean monthlyExhaustedDespiteNoUsage = limits.monthlyRequestLimit() > 0
                && monthlyRemainingRequests <= 0
                && usage.usedMonthlyRequests() <= 0;
        if (monthlyExhaustedDespiteNoUsage) {
            log.warn(
                    "Quota anomaly: monthly exhausted with zero usage. planType={} monthlyLimit={} usedMonthlyRequests={} usedDailyRequests={} estimatedMonthlyCostEur={}",
                    planType, limits.monthlyRequestLimit(), usage.usedMonthlyRequests(),
                    usage.usedDailyRequests(), usage.estimatedMonthlyCostEur()
            );
        }

        boolean requestAllowed = remainingRequests > 0
                && usage.estimatedMonthlyCostEur().compareTo(limits.monthlyCostCapEur()) < 0;

        int dailyUsagePercent = usagePercent(usage.usedDailyRequests(), limits.dailyRequestLimit());
        int monthlyUsagePercent = usagePercent(usage.usedMonthlyRequests(), limits.monthlyRequestLimit());

        QuotaState throttleState = determineThrottleState(Math.max(dailyUsagePercent, monthlyUsagePercent), requestAllowed);

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

    private QuotaState determineThrottleState(int monthlyUsagePercent, boolean requestAllowed) {
        if (!requestAllowed) {
            return QuotaState.CAPPED;
        }
        if (monthlyUsagePercent >= 90) {
            return QuotaState.RESTRICTED;
        }
        if (monthlyUsagePercent >= 70) {
            return QuotaState.CAREFUL;
        }
        return QuotaState.NORMAL;
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
