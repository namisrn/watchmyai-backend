package com.watchmyai.quota;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class UsageService {

    private static final String DEBUG_USER_ID = "debug-user";

    private final UserUsageRepository userUsageRepository;

    public UsageService(UserUsageRepository userUsageRepository) {
        this.userUsageRepository = userUsageRepository;
    }

    @Transactional
    public UsageSnapshot getCurrentUsage() {
        UserUsageEntity usage = getOrCreateCurrentUsage(PlanType.FREE);

        return new UsageSnapshot(
                usage.getUsedLifetimeRequests(),
                usage.getUsedMonthlyRequests(),
                usage.getUsedPremiumRequests(),
                usage.getEstimatedMonthlyCostEur()
        );
    }

    @Transactional
    public void recordRequest(PlanType planType, double estimatedRequestCostEur, boolean premiumRequest) {
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
        String currentPeriod = YearMonth.now().toString();

        return userUsageRepository
                .findByUserIdAndPeriodYearMonth(DEBUG_USER_ID, currentPeriod)
                .orElseGet(() -> userUsageRepository.save(
                        new UserUsageEntity(DEBUG_USER_ID, planType, currentPeriod)
                ));
    }
}