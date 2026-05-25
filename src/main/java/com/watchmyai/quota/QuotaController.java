package com.watchmyai.quota;

import com.watchmyai.subscription.SubscriptionStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quota")
public class QuotaController {

    private final QuotaService quotaService;
    private final UserPlanService userPlanService;
    private final SubscriptionStatusService subscriptionStatusService;

    public QuotaController(
            QuotaService quotaService,
            UserPlanService userPlanService,
            SubscriptionStatusService subscriptionStatusService
    ) {
        this.quotaService = quotaService;
        this.userPlanService = userPlanService;
        this.subscriptionStatusService = subscriptionStatusService;
    }

    @GetMapping("/status")
    public QuotaStatusResponse status() {
        // Trigger the lazy subscription-expiry + plan-downgrade-reset pipeline BEFORE
        // reading the cached plan. Without this, a direct `/quota/status` caller (admin
        // tools, third-party clients, the QuotaControllerTest harness) can read a stale
        // PLUS/PRO plan after the App Store entitlement has expired and the user is
        // already eligible for the FREE allowance. The aggregated `/api/v1/status`
        // endpoint already does this; keep both paths consistent so the FE behaves the
        // same regardless of which it polls.
        subscriptionStatusService.getCurrentStatus();
        QuotaCheckResult quota = quotaService.checkQuota(userPlanService.getCurrentPlan());
        return QuotaStatusResponse.from(quota);
    }
}
