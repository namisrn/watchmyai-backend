package com.watchmyai.subscription;

import org.springframework.stereotype.Service;

@Service
public class SubscriptionStatusService {

    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public SubscriptionStatusService(SubscriptionEntitlementService subscriptionEntitlementService) {
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    public SubscriptionStatusResponse getCurrentStatus() {
        return subscriptionEntitlementService.getCurrentStatus();
    }
}
