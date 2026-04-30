package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionStatusServiceTest {

    @Test
    void getCurrentStatusReturnsCurrentPaidPlanProduct() {
        UserPlanService userPlanService = mock(UserPlanService.class);
        when(userPlanService.getCurrentPlan())
                .thenReturn(PlanType.PRO);
        SubscriptionStatusService service = new SubscriptionStatusService(
                userPlanService,
                new SubscriptionProductCatalog()
        );

        SubscriptionStatusResponse status = service.getCurrentStatus();

        assertThat(status.planType()).isEqualTo(PlanType.PRO);
        assertThat(status.productId()).isEqualTo("watchmyai.pro.monthly");
        assertThat(status.verified()).isFalse();
    }

    @Test
    void getCurrentStatusReturnsFreeAsVerifiedFallback() {
        UserPlanService userPlanService = mock(UserPlanService.class);
        when(userPlanService.getCurrentPlan())
                .thenReturn(PlanType.FREE);
        SubscriptionStatusService service = new SubscriptionStatusService(
                userPlanService,
                new SubscriptionProductCatalog()
        );

        SubscriptionStatusResponse status = service.getCurrentStatus();

        assertThat(status.planType()).isEqualTo(PlanType.FREE);
        assertThat(status.productId()).isNull();
        assertThat(status.verified()).isTrue();
    }
}
