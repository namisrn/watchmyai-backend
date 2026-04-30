package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class SubscriptionProductCatalog {

    private static final Map<String, PlanType> PLAN_BY_PRODUCT_ID = Map.of(
            "watchmyai.plus.monthly", PlanType.PLUS,
            "watchmyai.pro.monthly", PlanType.PRO
    );

    public Optional<PlanType> findPlanType(String productId) {
        if (productId == null || productId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(PLAN_BY_PRODUCT_ID.get(productId.trim()));
    }

    public Optional<String> findProductId(PlanType planType) {
        return PLAN_BY_PRODUCT_ID
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() == planType)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
