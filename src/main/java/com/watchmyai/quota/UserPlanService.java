package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPlanService {

    private final UserPlanRepository userPlanRepository;
    private final UserContextService userContextService;

    public UserPlanService(
            UserPlanRepository userPlanRepository,
            UserContextService userContextService
    ) {
        this.userPlanRepository = userPlanRepository;
        this.userContextService = userContextService;
    }

    @Transactional
    public PlanType getCurrentPlan() {
        return getPlanForUser(userContextService.getCurrentUser().userId());
    }

    /**
     * Plan lookup for an explicit user. Used by `SubscriptionTransactionService` to read
     * the previous plan *before* `setCurrentPlanForUser` writes the new one — so the
     * downgrade-detection logic can compare old vs new and trigger a usage reset.
     */
    @Transactional(readOnly = true)
    public PlanType getPlanForUser(String userId) {
        return userPlanRepository
                .findByUserId(userId)
                .map(UserPlanEntity::getPlanType)
                .orElse(PlanType.FREE);
    }

    @Transactional
    public PlanType setCurrentPlan(PlanType planType) {
        return setCurrentPlanForUser(userContextService.getCurrentUser().userId(), planType);
    }

    @Transactional
    public PlanType setCurrentPlanForUser(String userId, PlanType planType) {
        UserPlanEntity userPlan = userPlanRepository
                .findByUserId(userId)
                .orElseGet(() -> new UserPlanEntity(userId, planType));

        userPlan.setPlanType(planType);
        userPlanRepository.save(userPlan);

        return planType;
    }
}
