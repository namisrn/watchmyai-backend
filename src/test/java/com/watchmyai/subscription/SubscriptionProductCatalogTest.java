package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionProductCatalogTest {

    private final SubscriptionProductCatalog productCatalog = new SubscriptionProductCatalog();

    @Test
    void findPlanTypeMapsKnownProductIds() {
        assertThat(productCatalog.findPlanType("watchmyai.plus.monthly"))
                .contains(PlanType.PLUS);
        assertThat(productCatalog.findPlanType("watchmyai.pro.monthly"))
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
                .contains("watchmyai.plus.monthly");
        assertThat(productCatalog.findProductId(PlanType.PRO))
                .contains("watchmyai.pro.monthly");
        assertThat(productCatalog.findProductId(PlanType.FREE))
                .isEmpty();
    }
}
