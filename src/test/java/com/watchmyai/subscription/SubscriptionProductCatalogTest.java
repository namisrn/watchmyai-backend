package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionProductCatalogTest {

    private final SubscriptionProductCatalog productCatalog = new SubscriptionProductCatalog();

    @Test
    void findPlanTypeMapsKnownProductIds() {
        assertThat(productCatalog.findPlanType(SubscriptionProductCatalog.PLUS_MONTHLY_PRODUCT_ID))
                .contains(PlanType.PLUS);
        assertThat(productCatalog.findPlanType(SubscriptionProductCatalog.PRO_MONTHLY_PRODUCT_ID))
                .contains(PlanType.PRO);
    }

    @Test
    void findPlanTypeIgnoresUnknownProductIds() {
        assertThat(productCatalog.findPlanType("unknown.product"))
                .isEmpty();
    }

    @Test
    void findProductIdMapsPaidPlans() {
        assertThat(productCatalog.findProductId(PlanType.PLUS))
                .contains(SubscriptionProductCatalog.PLUS_MONTHLY_PRODUCT_ID);
        assertThat(productCatalog.findProductId(PlanType.PRO))
                .contains(SubscriptionProductCatalog.PRO_MONTHLY_PRODUCT_ID);
        assertThat(productCatalog.findProductId(PlanType.FREE))
                .isEmpty();
    }
}
