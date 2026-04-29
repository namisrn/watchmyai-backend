package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class UsageService {

    private final UserUsageRepository userUsageRepository;
    private final UserContextService userContextService;

    public UsageService(
            UserUsageRepository userUsageRepository,
            UserContextService userContextService
    ) {
        this.userUsageRepository = userUsageRepository;
        this.userContextService = userContextService;
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
        String userId = userContextService.getCurrentUser().userId();

        return userUsageRepository
                .findByUserIdAndPeriodYearMonth(userId, currentPeriod)
                .orElseGet(() -> userUsageRepository.save(
                        new UserUsageEntity(userId, planType, currentPeriod)
                ));
    }
}