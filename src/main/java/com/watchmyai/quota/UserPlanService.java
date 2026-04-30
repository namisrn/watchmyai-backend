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
        String userId = userContextService.getCurrentUser().userId();

        return userPlanRepository
                .findByUserId(userId)
                .map(UserPlanEntity::getPlanType)
                .orElse(PlanType.FREE);
    }

    @Transactional
    public PlanType setCurrentPlan(PlanType planType) {
        String userId = userContextService.getCurrentUser().userId();
        UserPlanEntity userPlan = userPlanRepository
                .findByUserId(userId)
                .orElseGet(() -> new UserPlanEntity(userId, planType));

        userPlan.setPlanType(planType);
        userPlanRepository.save(userPlan);

        return planType;
    }
}
