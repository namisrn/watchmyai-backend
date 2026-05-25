package com.watchmyai.quota;

import com.watchmyai.subscription.SubscriptionProductCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanCatalogController {

    private final PlanConfigService planConfigService;
    private final SubscriptionProductCatalog productCatalog;

    public PlanCatalogController(
            PlanConfigService planConfigService,
            SubscriptionProductCatalog productCatalog
    ) {
        this.planConfigService = planConfigService;
        this.productCatalog = productCatalog;
    }

    @GetMapping
    public PlanCatalogResponse plans() {
        return PlanCatalogResponse.from(planConfigService.allLimits(), productCatalog);
    }
}
