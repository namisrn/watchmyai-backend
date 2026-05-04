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
        AppStoreServerService.VerificationResult verificationResult =
                appStoreServerService.verifyClientTransactionPayload(request.signedTransactionInfo());

        String sourceProductId = request.productId();
        String sourceTransactionId = request.transactionId();
        String sourceOriginalTransactionId = request.originalTransactionId();
        String sourceEnvironment = request.environment();

        if (verificationResult.verified()) {
            sourceProductId = verificationResult.payload().getProductId();
            sourceTransactionId = verificationResult.payload().getTransactionId();
            sourceOriginalTransactionId = verificationResult.payload().getOriginalTransactionId();
            sourceEnvironment = verificationResult.payload().getEnvironment() != null
                    ? verificationResult.payload().getEnvironment().toString()
                    : sourceEnvironment;
        }

        PlanType planType = productCatalog
                .findPlanType(sourceProductId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription product."));

        userPlanService.setCurrentPlan(planType);

        return new SubscriptionStatusResponse(
                planType,
                sourceProductId,
                verificationResult.verified(),
                verificationResult.verificationSource(),
                sourceTransactionId,
                sourceOriginalTransactionId,
                sourceEnvironment
        );
    }
}
