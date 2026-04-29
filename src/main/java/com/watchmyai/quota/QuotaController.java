package com.watchmyai.quota;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quota")
public class QuotaController {

    private final QuotaService quotaService;
    private final UserPlanService userPlanService;

    public QuotaController(
            QuotaService quotaService,
            UserPlanService userPlanService
    ) {
        this.quotaService = quotaService;
        this.userPlanService = userPlanService;
    }

    @GetMapping("/status")
    public QuotaStatusResponse status() {
        QuotaCheckResult quota = quotaService.checkQuota(userPlanService.getCurrentPlan());
        return QuotaStatusResponse.from(quota);
    }
}
