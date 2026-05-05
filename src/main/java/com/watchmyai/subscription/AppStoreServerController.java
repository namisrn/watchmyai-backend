package com.watchmyai.subscription;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app-store")
public class AppStoreServerController {

    private final AppStoreServerService appStoreServerService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public AppStoreServerController(
            AppStoreServerService appStoreServerService,
            SubscriptionEntitlementService subscriptionEntitlementService
    ) {
        this.appStoreServerService = appStoreServerService;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    @GetMapping("/status")
    public AppStoreServerStatusResponse status() {
        return appStoreServerService.status();
    }

    @PostMapping("/notifications")
    public AppStoreNotificationResponse notifications(@Valid @RequestBody AppStoreNotificationRequest request) {
        var payload = appStoreServerService.verifyNotificationPayload(request.signedPayload());
        if (payload == null || payload.getData() == null || payload.getData().getSignedTransactionInfo() == null) {
            return new AppStoreNotificationResponse(true, "jws_shape_only");
        }

        var verificationResult = appStoreServerService.verifyClientTransactionPayload(
                payload.getData().getSignedTransactionInfo()
        );
        return subscriptionEntitlementService.applyNotification(payload, verificationResult.payload());
    }
}
