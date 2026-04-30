package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SubscriptionSyncServiceTest {

    @Test
    void syncStoresPaidPlanForKnownProduct() {
        UserPlanService userPlanService = mock(UserPlanService.class);
        SubscriptionSyncService service = new SubscriptionSyncService(
                new SubscriptionProductCatalog(),
                userPlanService
        );

        SubscriptionStatusResponse response = service.sync(new SubscriptionSyncRequest(
                "watchmyai.pro.monthly",
                "transaction-1",
                "original-1",
                "sandbox",
                "signed-jws"
        ));

        assertThat(response.planType()).isEqualTo(PlanType.PRO);
        assertThat(response.productId()).isEqualTo("watchmyai.pro.monthly");
        assertThat(response.verified()).isFalse();
        assertThat(response.verificationSource()).isEqualTo("client_verified");
        assertThat(response.transactionId()).isEqualTo("transaction-1");
        verify(userPlanService).setCurrentPlan(PlanType.PRO);
    }

    @Test
    void syncRejectsUnknownProduct() {
        SubscriptionSyncService service = new SubscriptionSyncService(
                new SubscriptionProductCatalog(),
                mock(UserPlanService.class)
        );

        assertThatThrownBy(() -> service.sync(new SubscriptionSyncRequest(
                "unknown",
                "transaction-1",
                "original-1",
                "sandbox",
                "signed-jws"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown subscription product.");
    }
}
