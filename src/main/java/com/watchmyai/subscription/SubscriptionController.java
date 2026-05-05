package com.watchmyai.subscription;

import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final SubscriptionStatusService subscriptionStatusService;
    private final SubscriptionSyncService subscriptionSyncService;

    public SubscriptionController(
            SubscriptionStatusService subscriptionStatusService,
            SubscriptionSyncService subscriptionSyncService
    ) {
        this.subscriptionStatusService = subscriptionStatusService;
        this.subscriptionSyncService = subscriptionSyncService;
    }

    @GetMapping("/status")
    public SubscriptionStatusResponse status() {
        return subscriptionStatusService.getCurrentStatus();
    }

    @PostMapping("/sync")
    public SubscriptionStatusResponse sync(@Valid @RequestBody SubscriptionSyncRequest request) {
        return subscriptionSyncService.sync(request);
    }
}
