package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SubscriptionSyncServiceTest {

    @Test
    void syncStoresPaidPlanForKnownProduct() {
        UserPlanService userPlanService = mock(UserPlanService.class);
        SubscriptionSyncService service = new SubscriptionSyncService(
                new SubscriptionProductCatalog(),
                userPlanService,
                appStoreServerService()
        );

        SubscriptionStatusResponse response = service.sync(new SubscriptionSyncRequest(
                "watchmyai.pro.monthly",
                "transaction-1",
                "original-1",
                "sandbox",
                "header.payload.signature"
        ));

        assertThat(response.planType()).isEqualTo(PlanType.PRO);
        assertThat(response.productId()).isEqualTo("watchmyai.pro.monthly");
        assertThat(response.verified()).isFalse();
        assertThat(response.verificationSource()).isEqualTo("jws_shape_only");
        assertThat(response.transactionId()).isEqualTo("transaction-1");
        verify(userPlanService).setCurrentPlan(PlanType.PRO);
    }

    @Test
    void syncRejectsUnknownProduct() {
        SubscriptionSyncService service = new SubscriptionSyncService(
                new SubscriptionProductCatalog(),
                mock(UserPlanService.class),
                appStoreServerService()
        );

        assertThatThrownBy(() -> service.sync(new SubscriptionSyncRequest(
                "unknown",
                "transaction-1",
                "original-1",
                "sandbox",
                "header.payload.signature"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown subscription product.");
    }

    private AppStoreServerService appStoreServerService() {
        AppStoreServerService service = mock(AppStoreServerService.class);
        doReturn(AppStoreServerService.VerificationResult.jwsShapeOnly())
                .when(service)
                .verifyClientTransactionPayload("header.payload.signature");
        return service;
    }
}
