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

    public AppStoreServerController(AppStoreServerService appStoreServerService) {
        this.appStoreServerService = appStoreServerService;
    }

    @GetMapping("/status")
    public AppStoreServerStatusResponse status() {
        return appStoreServerService.status();
    }

    @PostMapping("/notifications")
    public AppStoreNotificationResponse notifications(@Valid @RequestBody AppStoreNotificationRequest request) {
        return appStoreServerService.acceptNotification(request);
    }
}
