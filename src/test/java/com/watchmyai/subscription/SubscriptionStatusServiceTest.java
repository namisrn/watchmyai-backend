package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SubscriptionStatusServiceTest {

    @Test
    void getCurrentStatusReturnsCurrentPaidPlanProduct() {
        SubscriptionEntitlementService entitlementService = mock(SubscriptionEntitlementService.class);
        doReturn(new SubscriptionStatusResponse(PlanType.PRO, "watchmyai.pro.monthly", true))
                .when(entitlementService)
                .getCurrentStatus();
        SubscriptionStatusService service = new SubscriptionStatusService(entitlementService);

        SubscriptionStatusResponse status = service.getCurrentStatus();

        assertThat(status.planType()).isEqualTo(PlanType.PRO);
        assertThat(status.productId()).isEqualTo("watchmyai.pro.monthly");
        assertThat(status.verified()).isTrue();
    }

    @Test
    void getCurrentStatusReturnsFreeAsVerifiedFallback() {
        SubscriptionEntitlementService entitlementService = mock(SubscriptionEntitlementService.class);
        doReturn(new SubscriptionStatusResponse(PlanType.FREE, null, true))
                .when(entitlementService)
                .getCurrentStatus();
        SubscriptionStatusService service = new SubscriptionStatusService(entitlementService);

        SubscriptionStatusResponse status = service.getCurrentStatus();

        assertThat(status.planType()).isEqualTo(PlanType.FREE);
        assertThat(status.productId()).isNull();
        assertThat(status.verified()).isTrue();
    }
}
