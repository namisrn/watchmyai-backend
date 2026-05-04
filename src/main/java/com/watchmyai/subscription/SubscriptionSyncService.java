package com.watchmyai.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionSyncService {

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
        return subscriptionEntitlementService.syncFromClient(request, verificationResult);
    }
}
