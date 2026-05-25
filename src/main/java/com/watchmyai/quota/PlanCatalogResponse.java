package com.watchmyai.quota;

import com.watchmyai.subscription.SubscriptionProductCatalog;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record PlanCatalogResponse(List<PlanCatalogItemResponse> plans) {

    public static PlanCatalogResponse from(
            Map<PlanType, PlanLimits> limitsByPlan,
            SubscriptionProductCatalog productCatalog
    ) {
        List<PlanCatalogItemResponse> plans = Arrays
                .stream(PlanType.values())
                .map(planType -> PlanCatalogItemResponse.from(
                        limitsByPlan.get(planType),
                        productCatalog.findProductId(planType).orElse(null)
                ))
                .toList();
        return new PlanCatalogResponse(plans);
    }
}

record PlanCatalogItemResponse(
        String planType,
        String productId,
        int lifetimeRequestLimit,
        int dailyRequestLimit,
        int monthlyRequestLimit,
        int monthlyPremiumRequestLimit,
        int maxOutputTokens,
        BigDecimal monthlyCostCapEur
) {
    static PlanCatalogItemResponse from(PlanLimits limits, String productId) {
        return new PlanCatalogItemResponse(
                limits.planType().name(),
                productId,
                limits.lifetimeRequestLimit(),
                limits.dailyRequestLimit(),
                limits.monthlyRequestLimit(),
                limits.monthlyPremiumRequestLimit(),
                limits.maxOutputTokens(),
                limits.monthlyCostCapEur()
        );
    }
}
