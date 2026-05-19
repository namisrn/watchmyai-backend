package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SubscriptionSyncServiceTest {

    @Test
    void syncStoresPaidPlanForKnownProduct() {
        SubscriptionEntitlementService entitlementService = mock(SubscriptionEntitlementService.class);
        SubscriptionSyncRequest request = new SubscriptionSyncRequest(
                "watchmyai.pro.monthly",
                "transaction-1",
                "original-1",
                "sandbox",
                "header.payload.signature",
                "de305d54-75b4-431b-adb2-eb6b9e546014"
        );
        doReturn(new SubscriptionStatusResponse(
                PlanType.PRO,
                "watchmyai.pro.monthly",
                false,
                "jws_shape_only",
                "transaction-1",
                "original-1",
                "sandbox"
        )).when(entitlementService).syncFromClient(
                request,
                AppStoreServerService.VerificationResult.jwsShapeOnly()
        );

        SubscriptionSyncService service = new SubscriptionSyncService(
                appStoreServerService(),
                entitlementService,
                new SubscriptionProductCatalog()
        );

        SubscriptionStatusResponse response = service.sync(request);

        assertThat(response.planType()).isEqualTo(PlanType.PRO);
        assertThat(response.productId()).isEqualTo("watchmyai.pro.monthly");
        assertThat(response.verified()).isFalse();
        assertThat(response.verificationSource()).isEqualTo("jws_shape_only");
        assertThat(response.transactionId()).isEqualTo("transaction-1");
    }

    private AppStoreServerService appStoreServerService() {
        AppStoreServerService service = mock(AppStoreServerService.class);
        doReturn(AppStoreServerService.VerificationResult.jwsShapeOnly())
                .when(service)
                .verifyClientTransactionPayload("header.payload.signature");
        return service;
    }
}
