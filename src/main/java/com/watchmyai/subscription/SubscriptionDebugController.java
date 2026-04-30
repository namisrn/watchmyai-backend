package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/v1/subscription/debug")
public class SubscriptionDebugController {

    private final SubscriptionProductCatalog productCatalog;
    private final UserPlanService userPlanService;

    public SubscriptionDebugController(
            SubscriptionProductCatalog productCatalog,
            UserPlanService userPlanService
    ) {
        this.productCatalog = productCatalog;
        this.userPlanService = userPlanService;
    }

    @PostMapping("/sync")
    public SubscriptionStatusResponse sync(@Valid @RequestBody SubscriptionDebugSyncRequest request) {
        PlanType planType = productCatalog
                .findPlanType(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription product."));

        userPlanService.setCurrentPlan(planType);

        return new SubscriptionStatusResponse(planType, request.productId(), false);
    }
}
