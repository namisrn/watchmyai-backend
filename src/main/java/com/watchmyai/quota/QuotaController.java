package com.watchmyai.quota;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quota")
public class QuotaController {

    private final QuotaService quotaService;
    private final DebugPlanService debugPlanService;

    public QuotaController(
            QuotaService quotaService,
            DebugPlanService debugPlanService
    ) {
        this.quotaService = quotaService;
        this.debugPlanService = debugPlanService;
    }

    @GetMapping("/debug")
    public QuotaCheckResult debugQuota() {
        // TODO: Später aus echtem User-/Subscription-Kontext laden.
        return quotaService.checkQuota(debugPlanService.getCurrentPlan());
    }

    @PostMapping("/debug/plan/{planType}")
    public QuotaCheckResult changeDebugPlan(@PathVariable PlanType planType) {
        debugPlanService.setCurrentPlan(planType);
        return quotaService.checkQuota(planType);
    }

    @PostMapping("/debug/reset")
    public QuotaCheckResult resetDebugUsage() {
        quotaService.resetUsage();
        return quotaService.checkQuota(debugPlanService.getCurrentPlan());
    }

    @PostMapping("/debug/cost/high")
    public QuotaCheckResult simulateHighCost() {
        quotaService.simulateHighCost();
        return quotaService.checkQuota(debugPlanService.getCurrentPlan());
    }
}
