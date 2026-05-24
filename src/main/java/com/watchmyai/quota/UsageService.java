package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class UsageService {

    private final UserUsageRepository userUsageRepository;
    private final UserContextService userContextService;
    private final UserPlanService userPlanService;
    private final Clock clock;

    public UsageService(
            UserUsageRepository userUsageRepository,
            UserContextService userContextService,
            UserPlanService userPlanService,
            Clock clock
    ) {
        this.userUsageRepository = userUsageRepository;
        this.userContextService = userContextService;
        this.userPlanService = userPlanService;
        this.clock = clock;
    }

    @Transactional
    public UsageSnapshot getCurrentUsage() {
        UserUsageEntity usage = getOrCreateCurrentUsage(userPlanService.getCurrentPlan());

        return toSnapshot(usage);
    }

    @Transactional
    public void recordRequest(PlanType planType, BigDecimal estimatedRequestCostEur, boolean premiumRequest) {
        UserUsageEntity usage = getOrCreateCurrentUsage(planType);
        usage.setPlanType(planType);
        usage.incrementDailyRequests();
        usage.incrementMonthlyRequests();

        if (planType == PlanType.FREE) {
            usage.incrementLifetimeRequests();
        }

        if (premiumRequest) {
            usage.incrementPremiumRequests();
        }

        usage.addEstimatedMonthlyCostEur(estimatedRequestCostEur);
    }

    @Transactional
    public void resetUsage() {
        UserUsageEntity usage = getOrCreateCurrentUsage(PlanType.FREE);
        usage.reset();
    }

    @Transactional
    public void simulateHighCost() {
        UserUsageEntity usage = getOrCreateCurrentUsage(PlanType.FREE);
        usage.simulateHighCost();
    }

    private UserUsageEntity getOrCreateCurrentUsage(PlanType planType) {
        return getOrCreateCurrentUsageForUser(getCurrentUserId(), planType);
    }

    /**
     * userId-aware variant of {@link #getOrCreateCurrentUsage(PlanType)} for code paths
     * that run outside an HTTP request scope (e.g. App Store Server Notification handlers).
     */
    private UserUsageEntity getOrCreateCurrentUsageForUser(String userId, PlanType planType) {
        String periodYearMonth = getCurrentPeriodYearMonth();
        String periodDay = getCurrentPeriodDay();

        return userUsageRepository
                .findByUserIdAndPeriodYearMonth(userId, periodYearMonth)
                .map(usage -> resetDailyUsageIfNeeded(usage, periodDay))
                .orElseGet(() -> createUsage(userId, planType, periodYearMonth, periodDay));
    }

    private UserUsageEntity createUsage(String userId, PlanType planType, String periodYearMonth, String periodDay) {
        return userUsageRepository.save(
                new UserUsageEntity(userId, planType, periodYearMonth, periodDay)
        );
    }

    private UserUsageEntity resetDailyUsageIfNeeded(UserUsageEntity usage, String periodDay) {
        if (!periodDay.equals(usage.getPeriodDay())) {
            usage.resetDailyUsage(periodDay);
        }

        return usage;
    }

    private String getCurrentUserId() {
        return userContextService.getCurrentUser().userId();
    }

    private String getCurrentPeriodYearMonth() {
        return YearMonth.now(clock).toString();
    }

    private String getCurrentPeriodDay() {
        return LocalDate.now(clock).toString();
    }

    /**
     * Resets the current period's daily / monthly / premium counters for the given user.
     * Called after a subscription downgrade (e.g. Plus → Free at end-of-billing-period)
     * so the user's prior usage on the higher plan doesn't immediately exceed the new
     * lower limit and lock them out. The lifetime counter and accumulated EUR cost are
     * preserved — those are historical, plan-independent ledger entries.
     */
    @Transactional
    public void resetUsageForPlanDowngrade(String userId, PlanType newPlanType) {
        UserUsageEntity usage = getOrCreateCurrentUsageForUser(userId, newPlanType);
        usage.resetForPlanDowngrade();
        userUsageRepository.save(usage);
    }

    private UsageSnapshot toSnapshot(UserUsageEntity usage) {
        return new UsageSnapshot(
                usage.getUsedLifetimeRequests(),
                usage.getUsedDailyRequests(),
                usage.getUsedMonthlyRequests(),
                usage.getUsedPremiumRequests(),
                usage.getEstimatedMonthlyCostEur()
        );
    }
}
