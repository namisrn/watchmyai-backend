package com.watchmyai.subscription;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final SubscriptionStatusService subscriptionStatusService;

    public SubscriptionController(SubscriptionStatusService subscriptionStatusService) {
        this.subscriptionStatusService = subscriptionStatusService;
    }

    @GetMapping("/status")
    public SubscriptionStatusResponse status() {
        return subscriptionStatusService.getCurrentStatus();
    }
}
