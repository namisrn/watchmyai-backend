package com.watchmyai.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSyncService.class);

    private final AppStoreServerService appStoreServerService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public SubscriptionSyncService(
            AppStoreServerService appStoreServerService,
            SubscriptionEntitlementService subscriptionEntitlementService
    ) {
        this.appStoreServerService = appStoreServerService;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    @Transactional
    public SubscriptionStatusResponse sync(SubscriptionSyncRequest request) {
        AppStoreServerService.VerificationResult verificationResult =
                appStoreServerService.verifyClientTransactionPayload(request.signedTransactionInfo());
        SubscriptionStatusResponse response = subscriptionEntitlementService.syncFromClient(request, verificationResult);
        log.info(
                "Subscription sync completed productId={} transactionId={} originalTransactionId={} environment={} verified={} source={} resultingPlan={}",
                request.productId(),
                request.transactionId(),
                request.originalTransactionId(),
                request.environment(),
                response.verified(),
                response.verificationSource(),
                response.planType()
        );
        return response;
    }
}
