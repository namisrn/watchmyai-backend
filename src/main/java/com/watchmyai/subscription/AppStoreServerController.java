package com.watchmyai.subscription;

import com.watchmyai.user.UserContextService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/app-store")
public class AppStoreServerController {

    private static final Logger log = LoggerFactory.getLogger(AppStoreServerController.class);
    private static final String NOTIFICATION_KEY_PREFIX = "watchmyai:appstore-notification:";
    private static final Duration NOTIFICATION_TTL = Duration.ofDays(7);

    private final AppStoreServerService appStoreServerService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;
    private final UserContextService userContextService;
    /// Redis is **required** for Apple-S2S notification dedup. Direct (non-`ObjectProvider`)
    /// injection makes the bean a hard dependency — Spring refuses to start the application
    /// if `StringRedisTemplate` is missing, surfacing the misconfiguration at startup instead
    /// of letting duplicate notifications silently apply twice (plan downgrade × 2, refund × 2).
    private final StringRedisTemplate redisTemplate;
    private final Counter notificationDuplicateCounter;

    public AppStoreServerController(
            AppStoreServerService appStoreServerService,
            SubscriptionEntitlementService subscriptionEntitlementService,
            UserContextService userContextService,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry
    ) {
        this.appStoreServerService = appStoreServerService;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
        this.userContextService = userContextService;
        this.redisTemplate = redisTemplate;
        this.notificationDuplicateCounter = meterRegistry.counter("watchmyai.appstore.notification_duplicate");
    }

    @GetMapping("/status")
    public AppStoreServerStatusResponse status() {
        // Diagnostic endpoint — require an authenticated caller so App Store configuration
        // details are not exposed publicly.
        userContextService.getCurrentUser();
        return appStoreServerService.status();
    }

    @PostMapping("/notifications")
    public AppStoreNotificationResponse notifications(@Valid @RequestBody AppStoreNotificationRequest request) {
        var payload = appStoreServerService.verifyNotificationPayload(request.signedPayload());
        if (payload == null || payload.getData() == null || payload.getData().getSignedTransactionInfo() == null) {
            return new AppStoreNotificationResponse(true, "jws_shape_only");
        }

        String notificationUUID = payload.getNotificationUUID();
        if (notificationUUID != null && !notificationUUID.isBlank() && !markAsSeen(notificationUUID)) {
            notificationDuplicateCounter.increment();
            log.info("Duplicate App Store notification ignored notificationUUID={}", notificationUUID);
            return new AppStoreNotificationResponse(true, "duplicate");
        }

        var verificationResult = appStoreServerService.verifyClientTransactionPayload(
                payload.getData().getSignedTransactionInfo()
        );
        return subscriptionEntitlementService.applyNotification(payload, verificationResult.payload());
    }

    private boolean markAsSeen(String notificationUUID) {
        String key = NOTIFICATION_KEY_PREFIX + notificationUUID;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", NOTIFICATION_TTL);
        return Boolean.TRUE.equals(isNew);
    }
}
