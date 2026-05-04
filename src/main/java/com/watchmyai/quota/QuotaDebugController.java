package com.watchmyai.quota;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@ConditionalOnProperty(value = "watchmyai.debug.endpoints-enabled", havingValue = "true")
@RestController
@RequestMapping("/api/v1/quota/debug")
public class QuotaDebugController {

    private final QuotaService quotaService;
    private final UserPlanService userPlanService;

    public QuotaDebugController(
            QuotaService quotaService,
            UserPlanService userPlanService
    ) {
        this.quotaService = quotaService;
        this.userPlanService = userPlanService;
    }

    @GetMapping
    public QuotaCheckResult debugQuota() {
        return quotaService.checkQuota(userPlanService.getCurrentPlan());
    }

    @PostMapping("/plan/{planType}")
    public QuotaCheckResult changeDebugPlan(@PathVariable PlanType planType) {
        PlanType currentPlan = userPlanService.setCurrentPlan(planType);
        return quotaService.checkQuota(currentPlan);
    }

    @PostMapping("/reset")
    public QuotaCheckResult resetDebugUsage() {
        quotaService.resetUsage();
        return quotaService.checkQuota(userPlanService.getCurrentPlan());
    }

    @PostMapping("/cost/high")
    public QuotaCheckResult simulateHighCost() {
        quotaService.simulateHighCost();
        return quotaService.checkQuota(userPlanService.getCurrentPlan());
    }
}
