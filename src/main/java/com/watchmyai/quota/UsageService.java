package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

@Service
public class UsageService {

    private final UserUsageRepository userUsageRepository;
    private final UserContextService userContextService;
    private final Clock clock;

    public UsageService(
            UserUsageRepository userUsageRepository,
            UserContextService userContextService,
            Clock clock
    ) {
        this.userUsageRepository = userUsageRepository;
        this.userContextService = userContextService;
        this.clock = clock;
    }

    @Transactional
    public UsageSnapshot getCurrentUsage() {
        UserUsageEntity usage = getOrCreateCurrentUsage(PlanType.FREE);

        return toSnapshot(usage);
    }

    @Transactional
    public void recordRequest(PlanType planType, BigDecimal estimatedRequestCostEur, boolean premiumRequest) {
        UserUsageEntity usage = getOrCreateCurrentUsage(planType);
        usage.setPlanType(planType);

        if (planType == PlanType.FREE) {
            usage.incrementLifetimeRequests();
        } else {
            usage.incrementMonthlyRequests();
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
        String userId = getCurrentUserId();
        String periodYearMonth = getCurrentPeriodYearMonth();

        return userUsageRepository
                .findByUserIdAndPeriodYearMonth(userId, periodYearMonth)
                .orElseGet(() -> createUsage(userId, planType, periodYearMonth));
    }

    private UserUsageEntity createUsage(String userId, PlanType planType, String periodYearMonth) {
        return userUsageRepository.save(
                new UserUsageEntity(userId, planType, periodYearMonth)
        );
    }

    private String getCurrentUserId() {
        return userContextService.getCurrentUser().userId();
    }

    private String getCurrentPeriodYearMonth() {
        return YearMonth.now(clock).toString();
    }

    private UsageSnapshot toSnapshot(UserUsageEntity usage) {
        return new UsageSnapshot(
                usage.getUsedLifetimeRequests(),
                usage.getUsedMonthlyRequests(),
                usage.getUsedPremiumRequests(),
                usage.getEstimatedMonthlyCostEur()
        );
    }
}
