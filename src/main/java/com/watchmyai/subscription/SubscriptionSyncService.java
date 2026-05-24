package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SubscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSyncService.class);

    private final AppStoreServerService appStoreServerService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;
    private final SubscriptionProductCatalog productCatalog;

    public SubscriptionSyncService(
            AppStoreServerService appStoreServerService,
            SubscriptionEntitlementService subscriptionEntitlementService,
            SubscriptionProductCatalog productCatalog
    ) {
        this.appStoreServerService = appStoreServerService;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
        this.productCatalog = productCatalog;
    }

    @Transactional
    public SubscriptionStatusResponse sync(SubscriptionSyncRequest request) {
        AppStoreServerService.VerificationResult verificationResult =
                appStoreServerService.verifyClientTransactionPayload(request.signedTransactionInfo());
        SubscriptionStatusResponse response = subscriptionEntitlementService.syncFromClient(request, verificationResult);
        String verifiedProductId = verificationResult.payload() == null
                ? null
                : verificationResult.payload().getProductId();
        Optional<PlanType> requestedPlan = productCatalog.findPlanType(request.productId());
        Optional<PlanType> verifiedPlan = productCatalog.findPlanType(verifiedProductId);
        log.info(
                "Subscription sync completed requestedProductId={} verifiedProductId={} transactionId={} originalTransactionId={} environment={} requestedPlan={} verifiedPlan={} backendPlan={} resultingProductId={} verified={} source={}",
                request.productId(),
                verifiedProductId,
                request.transactionId(),
                request.originalTransactionId(),
                request.environment(),
                requestedPlan.map(Enum::name).orElse("UNKNOWN"),
                verifiedPlan.map(Enum::name).orElse("UNKNOWN"),
                response.planType(),
                response.productId(),
                response.verified(),
                response.verificationSource()
        );
        return response;
    }
}
