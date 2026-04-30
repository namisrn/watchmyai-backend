package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionSyncService {

    private final SubscriptionProductCatalog productCatalog;
    private final UserPlanService userPlanService;
    private final AppStoreServerService appStoreServerService;

    public SubscriptionSyncService(
            SubscriptionProductCatalog productCatalog,
            UserPlanService userPlanService,
            AppStoreServerService appStoreServerService
    ) {
        this.productCatalog = productCatalog;
        this.userPlanService = userPlanService;
        this.appStoreServerService = appStoreServerService;
    }

    @Transactional
    public SubscriptionStatusResponse sync(SubscriptionSyncRequest request) {
        PlanType planType = productCatalog
                .findPlanType(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription product."));

        appStoreServerService.verifyClientTransactionPayload(request.signedTransactionInfo());
        userPlanService.setCurrentPlan(planType);

        return new SubscriptionStatusResponse(
                planType,
                request.productId(),
                false,
                "storekit_jws_received",
                request.transactionId(),
                request.originalTransactionId(),
                request.environment()
        );
    }
}
