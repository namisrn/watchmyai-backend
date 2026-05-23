package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class UsageService {

    private final UserUsageRepository userUsageRepository;
    private final UserContextService userContextService;
    private final UserPlanService userPlanService;
    private final PlanConfigService planConfigService;
    private final Clock clock;

    public UsageService(
            UserUsageRepository userUsageRepository,
            UserContextService userContextService,
            UserPlanService userPlanService,
            PlanConfigService planConfigService,
            Clock clock
    ) {
        this.userUsageRepository = userUsageRepository;
        this.userContextService = userContextService;
        this.userPlanService = userPlanService;
        this.planConfigService = planConfigService;
        this.clock = clock;
    }

    @Transactional
    public UsageSnapshot getCurrentUsage() {
        UserUsageEntity usage = getOrCreateCurrentUsage(getCurrentUserId(), userPlanService.getCurrentPlan());

        return toSnapshot(usage);
    }

    /**
     * Reads the usage of an explicit user. Used by the AI worker pool, which runs outside the
     * HTTP request scope and therefore cannot resolve the user from the request context.
     */
    @Transactional
    public UsageSnapshot getUsage(String userId, PlanType planType) {
        return toSnapshot(getOrCreateCurrentUsage(userId, planType));
    }

    /**
     * Atomically reserves one request slot before the provider call. Returns {@code true}
     * when the slot was reserved, {@code false} when a quota limit is exhausted.
     */
    @Transactional
    public boolean reserveRequest(PlanType planType) {
        return reserveRequest(getCurrentUserId(), planType);
    }

    @Transactional
    public boolean reserveRequest(String userId, PlanType planType) {
        getOrCreateCurrentUsage(userId, planType);

        PlanLimits limits = planConfigService.getLimits(planType);
        int affected = userUsageRepository.reserveSlot(
                userId,
                getCurrentPeriodYearMonth(),
                planType,
                limits.dailyRequestLimit(),
                limits.monthlyRequestLimit(),
                limits.lifetimeRequestLimit(),
                lifetimeIncrement(planType),
                limits.monthlyCostCapEur(),
                Instant.now(clock)
        );

        return affected > 0;
    }

    /**
     * Reverts a previously reserved slot when the provider call failed.
     */
    @Transactional
    public void refundRequest(PlanType planType) {
        refundRequest(getCurrentUserId(), planType);
    }

    @Transactional
    public void refundRequest(String userId, PlanType planType) {
        userUsageRepository.refundSlot(
                userId,
                getCurrentPeriodYearMonth(),
                lifetimeIncrement(planType),
                Instant.now(clock)
        );
    }

    /**
     * Records the actual cost after a successful provider call. Premium-slot accounting
     * is handled separately by {@link #reservePremium} (atomic, before the provider call).
     */
    @Transactional
    public void finalizeRequest(PlanType planType, BigDecimal estimatedRequestCostEur) {
        finalizeRequest(getCurrentUserId(), planType, estimatedRequestCostEur);
    }

    @Transactional
    public void finalizeRequest(
            String userId,
            PlanType planType,
            BigDecimal estimatedRequestCostEur
    ) {
        userUsageRepository.finalizeCost(
                userId,
                getCurrentPeriodYearMonth(),
                estimatedRequestCostEur,
                Instant.now(clock)
        );
    }

    /**
     * Atomically reserves one premium slot. Returns {@code true} when the call may use the
     * premium model, {@code false} when the premium quota is already exhausted (or the plan
     * has no premium allotment). Must be paired with {@link #refundPremium} when the provider
     * call subsequently fails.
     */
    @Transactional
    public boolean reservePremium(String userId, PlanType planType) {
        PlanLimits limits = planConfigService.getLimits(planType);
        int affected = userUsageRepository.reservePremiumSlot(
                userId,
                getCurrentPeriodYearMonth(),
                limits.monthlyPremiumRequestLimit(),
                Instant.now(clock)
        );
        return affected > 0;
    }

    /**
     * Refunds a previously reserved premium slot when the provider call failed.
     */
    @Transactional
    public void refundPremium(String userId) {
        userUsageRepository.refundPremiumSlot(
                userId,
                getCurrentPeriodYearMonth(),
                Instant.now(clock)
        );
    }

    private int lifetimeIncrement(PlanType planType) {
        return planType == PlanType.FREE ? 1 : 0;
    }

    @Transactional
    public void resetUsage() {
        UserUsageEntity usage = getOrCreateCurrentUsage(getCurrentUserId(), PlanType.FREE);
        usage.reset();
    }

    @Transactional
    public void simulateHighCost() {
        UserUsageEntity usage = getOrCreateCurrentUsage(getCurrentUserId(), PlanType.FREE);
        usage.simulateHighCost();
    }

    private UserUsageEntity getOrCreateCurrentUsage(String userId, PlanType planType) {
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
