package com.watchmyai.quota;

import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/status")
    public QuotaStatusResponse status() {
        QuotaCheckResult quota = quotaService.checkQuota(debugPlanService.getCurrentPlan());
        return QuotaStatusResponse.from(quota);
    }
}
