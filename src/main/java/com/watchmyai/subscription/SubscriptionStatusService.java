package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionStatusService {

    private final UserPlanService userPlanService;
    private final SubscriptionProductCatalog productCatalog;

    public SubscriptionStatusService(
            UserPlanService userPlanService,
            SubscriptionProductCatalog productCatalog
    ) {
        this.userPlanService = userPlanService;
        this.productCatalog = productCatalog;
    }

    public SubscriptionStatusResponse getCurrentStatus() {
        PlanType planType = userPlanService.getCurrentPlan();

        return new SubscriptionStatusResponse(
                planType,
                productCatalog.findProductId(planType).orElse(null),
                planType == PlanType.FREE
        );
    }
}
