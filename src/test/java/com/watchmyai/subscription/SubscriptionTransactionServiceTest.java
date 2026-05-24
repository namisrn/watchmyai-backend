package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UsageService;
import com.watchmyai.quota.UserPlanService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SubscriptionTransactionServiceTest {

    private final AppStoreSubscriptionRepository subscriptionRepository = mock(AppStoreSubscriptionRepository.class);
    private final SubscriptionUpsertService subscriptionUpsertService = mock(SubscriptionUpsertService.class);
    private final SubscriptionProductCatalog productCatalog = mock(SubscriptionProductCatalog.class);
    private final UserPlanService userPlanService = mock(UserPlanService.class);
    private final UsageService usageService = mock(UsageService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    private final SubscriptionTransactionService service = new SubscriptionTransactionService(
            subscriptionRepository,
            subscriptionUpsertService,
            productCatalog,
            userPlanService,
            usageService,
            clock
    );

    @Test
    void refreshUserPlanPicksHighestActivePlan() {
        AppStoreSubscriptionEntity plus = activeSubscription("user-1", "orig-1", PlanType.PLUS);
        AppStoreSubscriptionEntity pro = activeSubscription("user-1", "orig-2", PlanType.PRO);
        when(subscriptionRepository.findByUserIdAndActiveTrue("user-1")).thenReturn(List.of(plus, pro));
        when(userPlanService.getPlanForUser("user-1")).thenReturn(PlanType.FREE);

        PlanType result = service.refreshUserPlan("user-1");

        assertThat(result).isEqualTo(PlanType.PRO);
        verify(userPlanService).setCurrentPlanForUser("user-1", PlanType.PRO);
    }

    @Test
    void refreshUserPlanFallsBackToFreeWhenNoActiveSubscription() {
        when(subscriptionRepository.findByUserIdAndActiveTrue("user-1")).thenReturn(List.of());
        when(userPlanService.getPlanForUser("user-1")).thenReturn(PlanType.PLUS);

        PlanType result = service.refreshUserPlan("user-1");

        assertThat(result).isEqualTo(PlanType.FREE);
        verify(userPlanService).setCurrentPlanForUser("user-1", PlanType.FREE);
        verify(usageService).resetUsageForPlanDowngrade("user-1", PlanType.FREE);
    }

    @Test
    void buildActiveStatusReturnsMostRecentlyVerifiedSubscriptionMatchingPlan() {
        Instant older = Instant.parse("2026-01-01T10:00:00Z");
        Instant newer = Instant.parse("2026-01-01T11:00:00Z");
        // lastVerifiedAt is passed as the final field in SubscriptionUpdatePayload
        AppStoreSubscriptionEntity old = activeSubscriptionVerified("user-1", "orig-1", PlanType.PRO, "tx-1", older);
        AppStoreSubscriptionEntity recent = activeSubscriptionVerified("user-1", "orig-2", PlanType.PRO, "tx-2", newer);
        when(subscriptionRepository.findByUserIdAndActiveTrue("user-1")).thenReturn(List.of(old, recent));

        SubscriptionStatusResponse response = service.buildActiveStatus("user-1", PlanType.PRO);

        assertThat(response.planType()).isEqualTo(PlanType.PRO);
        assertThat(response.transactionId()).isEqualTo("tx-2");
        assertThat(response.verified()).isTrue();
    }

    @Test
    void buildActiveStatusReturnsFreeWhenNoActiveSubscriptionMatchesPlan() {
        when(subscriptionRepository.findByUserIdAndActiveTrue("user-1")).thenReturn(List.of());

        SubscriptionStatusResponse response = service.buildActiveStatus("user-1", PlanType.FREE);

        assertThat(response.planType()).isEqualTo(PlanType.FREE);
        assertThat(response.verified()).isTrue();
    }

    @Test
    void getActiveStatusExpiresStaleSubscriptionsAndRefreshesPlan() {
        Instant expired = Instant.parse("2026-01-01T11:00:00Z"); // before clock fixed time
        AppStoreSubscriptionEntity stale = activeSubscriptionVerified("user-1", "orig-1", PlanType.PLUS, "tx-1", Instant.now());
        stale.update(payloadWithExpiry("tx-1", PlanType.PLUS, expired));

        when(subscriptionRepository.findByUserIdAndActiveTrue("user-1"))
                .thenReturn(List.of(stale))
                .thenReturn(List.of());
        when(userPlanService.getPlanForUser("user-1")).thenReturn(PlanType.PLUS);

        SubscriptionStatusResponse response = service.getActiveStatus("user-1");

        assertThat(stale.isActive()).isFalse();
        assertThat(response.planType()).isEqualTo(PlanType.FREE);
        verify(userPlanService).setCurrentPlanForUser("user-1", PlanType.FREE);
        verify(usageService).resetUsageForPlanDowngrade("user-1", PlanType.FREE);
    }

    @Test
    void getActiveStatusDoesNotExpireSubscriptionsThatAreStillValid() {
        Instant future = Instant.parse("2027-01-01T12:00:00Z");
        AppStoreSubscriptionEntity active = activeSubscriptionVerified("user-1", "orig-1", PlanType.PRO, "tx-1", Instant.now());
        active.update(payloadWithExpiry("tx-1", PlanType.PRO, future));

        when(subscriptionRepository.findByUserIdAndActiveTrue("user-1"))
                .thenReturn(List.of(active));
        when(userPlanService.getPlanForUser("user-1")).thenReturn(PlanType.PRO);

        SubscriptionStatusResponse response = service.getActiveStatus("user-1");

        assertThat(active.isActive()).isTrue();
        assertThat(response.planType()).isEqualTo(PlanType.PRO);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unverifiedXcodeSyncPersistsExpirationSoExpiredPaidPlanCanDropToFree() {
        long expiredAt = Instant.parse("2026-01-01T11:00:00Z").toEpochMilli();
        when(productCatalog.findPlanType("watchmyai.plus.monthly")).thenReturn(Optional.of(PlanType.PLUS));
        when(subscriptionRepository.findByUserIdAndActiveTrue("user-1")).thenReturn(List.of());
        when(userPlanService.getPlanForUser("user-1")).thenReturn(PlanType.PLUS);

        service.processUnverifiedTransaction(
                "user-1",
                new SubscriptionSyncRequest(
                        "watchmyai.plus.monthly",
                        "tx-1",
                        "orig-1",
                        "XCODE",
                        "header.payload.signature",
                        expiredAt,
                        null
                ),
                "jws_shape_only"
        );

        var callback = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(subscriptionUpsertService).upsert(eq("user-1"), eq("orig-1"), callback.capture());
        AppStoreSubscriptionEntity stored = new AppStoreSubscriptionEntity("user-1", "orig-1");
        ((Consumer<AppStoreSubscriptionEntity>) callback.getValue()).accept(stored);

        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getExpiresAt()).isEqualTo(Instant.ofEpochMilli(expiredAt));
        verify(userPlanService).setCurrentPlanForUser("user-1", PlanType.FREE);
        verify(usageService).resetUsageForPlanDowngrade("user-1", PlanType.FREE);
    }

    private AppStoreSubscriptionEntity activeSubscription(String userId, String originalTransactionId, PlanType planType) {
        AppStoreSubscriptionEntity entity = new AppStoreSubscriptionEntity(userId, originalTransactionId);
        entity.update(new SubscriptionUpdatePayload(
                "tx-" + originalTransactionId, "product." + planType.name().toLowerCase(), planType,
                "sandbox", null, "ACTIVE", true,
                null, null, null, false, false,
                "app_store_server_library", null, null, Instant.now(clock)
        ));
        return entity;
    }

    private AppStoreSubscriptionEntity activeSubscriptionVerified(
            String userId, String originalTransactionId, PlanType planType, String transactionId, Instant updatedAt
    ) {
        AppStoreSubscriptionEntity entity = new AppStoreSubscriptionEntity(userId, originalTransactionId);
        entity.update(new SubscriptionUpdatePayload(
                transactionId, "product." + planType.name().toLowerCase(), planType,
                "sandbox", null, "ACTIVE", true,
                null, null, null, false, false,
                "app_store_server_library", null, null, updatedAt
        ));
        return entity;
    }

    private SubscriptionUpdatePayload payloadWithExpiry(String transactionId, PlanType planType, Instant expiresAt) {
        return new SubscriptionUpdatePayload(
                transactionId, "product." + planType.name().toLowerCase(), planType,
                "sandbox", null, "ACTIVE", true,
                expiresAt, null, null, false, false,
                "app_store_server_library", null, null, Instant.now(clock)
        );
    }
}
