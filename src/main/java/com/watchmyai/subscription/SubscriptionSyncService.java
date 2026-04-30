package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionSyncService {

    private final SubscriptionProductCatalog productCatalog;
    private final UserPlanService userPlanService;

    public SubscriptionSyncService(
            SubscriptionProductCatalog productCatalog,
            UserPlanService userPlanService
    ) {
        this.productCatalog = productCatalog;
        this.userPlanService = userPlanService;
    }

    @Transactional
    public SubscriptionStatusResponse sync(SubscriptionSyncRequest request) {
        PlanType planType = productCatalog
                .findPlanType(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription product."));

        userPlanService.setCurrentPlan(planType);

        return new SubscriptionStatusResponse(
                planType,
                request.productId(),
                false,
                "client_verified",
                request.transactionId(),
                request.originalTransactionId(),
                request.environment()
        );
    }
}
