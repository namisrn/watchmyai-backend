package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class SubscriptionProductCatalog {

    public static final String PLUS_MONTHLY_PRODUCT_ID = "watchmyai.plus.monthly";
    public static final String PRO_MONTHLY_PRODUCT_ID = "watchmyai.pro.monthly";
    public static final String PLUS_YEARLY_PRODUCT_ID = "watchmyai.plus.yearly";
    public static final String PRO_YEARLY_PRODUCT_ID = "watchmyai.pro.yearly";

    private static final Map<String, PlanType> PLAN_BY_PRODUCT_ID = Map.of(
            PLUS_MONTHLY_PRODUCT_ID, PlanType.PLUS,
            PRO_MONTHLY_PRODUCT_ID, PlanType.PRO,
            PLUS_YEARLY_PRODUCT_ID, PlanType.PLUS,
            PRO_YEARLY_PRODUCT_ID, PlanType.PRO
    );

    public Optional<PlanType> findPlanType(String productId) {
        if (productId == null || productId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(PLAN_BY_PRODUCT_ID.get(productId.trim()));
    }

    /**
     * Canonical product ID for a plan tier. Always the monthly variant — yearly is
     * an opt-in selection in the paywall UI, never the default served via /plans.
     * The frontend's {@code plan.productId} fallback assumes monthly; changing this
     * to yearly would silently shift the default-purchase path.
     */
    public Optional<String> findProductId(PlanType planType) {
        return switch (planType) {
            case PLUS -> Optional.of(PLUS_MONTHLY_PRODUCT_ID);
            case PRO -> Optional.of(PRO_MONTHLY_PRODUCT_ID);
            default -> Optional.empty();
        };
    }
}
