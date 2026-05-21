package com.watchmyai.common.api;

import com.watchmyai.quota.QuotaService;
import com.watchmyai.quota.QuotaStatusResponse;
import com.watchmyai.quota.UserPlanService;
import com.watchmyai.subscription.AppStoreServerService;
import com.watchmyai.subscription.SubscriptionStatusService;
import com.watchmyai.user.AuthStatusResponse;
import com.watchmyai.user.UserContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregated status endpoint. Replaces four separate client polls
 * ({@code /auth/status}, {@code /subscription/status}, {@code /quota/status},
 * {@code /app-store/status}) with a single round trip.
 */
@RestController
@RequestMapping("/api/v1")
public class StatusController {

    private final UserContextService userContextService;
    private final SubscriptionStatusService subscriptionStatusService;
    private final QuotaService quotaService;
    private final UserPlanService userPlanService;
    private final AppStoreServerService appStoreServerService;

    public StatusController(
            UserContextService userContextService,
            SubscriptionStatusService subscriptionStatusService,
            QuotaService quotaService,
            UserPlanService userPlanService,
            AppStoreServerService appStoreServerService
    ) {
        this.userContextService = userContextService;
        this.subscriptionStatusService = subscriptionStatusService;
        this.quotaService = quotaService;
        this.userPlanService = userPlanService;
        this.appStoreServerService = appStoreServerService;
    }

    @GetMapping("/status")
    public AggregatedStatusResponse status() {
        return new AggregatedStatusResponse(
                AuthStatusResponse.from(userContextService.getCurrentUser()),
                subscriptionStatusService.getCurrentStatus(),
                QuotaStatusResponse.from(quotaService.checkQuota(userPlanService.getCurrentPlan())),
                appStoreServerService.status()
        );
    }
}
