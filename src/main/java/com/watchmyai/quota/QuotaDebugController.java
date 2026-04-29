package com.watchmyai.quota;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/v1/quota/debug")
public class QuotaDebugController {

    private final QuotaService quotaService;
    private final DebugPlanService debugPlanService;

    public QuotaDebugController(
            QuotaService quotaService,
            DebugPlanService debugPlanService
    ) {
        this.quotaService = quotaService;
        this.debugPlanService = debugPlanService;
    }

    @GetMapping
    public QuotaCheckResult debugQuota() {
        return quotaService.checkQuota(debugPlanService.getCurrentPlan());
    }

    @PostMapping("/plan/{planType}")
    public QuotaCheckResult changeDebugPlan(@PathVariable PlanType planType) {
        debugPlanService.setCurrentPlan(planType);
        return quotaService.checkQuota(planType);
    }

    @PostMapping("/reset")
    public QuotaCheckResult resetDebugUsage() {
        quotaService.resetUsage();
        return quotaService.checkQuota(debugPlanService.getCurrentPlan());
    }

    @PostMapping("/cost/high")
    public QuotaCheckResult simulateHighCost() {
        quotaService.simulateHighCost();
        return quotaService.checkQuota(debugPlanService.getCurrentPlan());
    }
}
