package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class SubscriptionProductCatalog {

    public static final String PLUS_MONTHLY_PRODUCT_ID = "watchmyai.plus.monthly";
    public static final String PRO_MONTHLY_PRODUCT_ID = "watchmyai.pro.monthly";

    private static final Map<String, PlanType> PLAN_BY_PRODUCT_ID = Map.of(
            PLUS_MONTHLY_PRODUCT_ID, PlanType.PLUS,
            PRO_MONTHLY_PRODUCT_ID, PlanType.PRO
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
