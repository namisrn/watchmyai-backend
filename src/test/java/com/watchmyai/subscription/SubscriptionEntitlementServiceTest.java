package com.watchmyai.subscription;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.watchmyai.quota.PlanType;
import com.watchmyai.user.AppUserService;
import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionEntitlementServiceTest {

    private static final String USER_ID = "apple:user-1";
    private static final String ORIGINAL_TRANSACTION_ID = "original-1";
    private static final UUID USER_TOKEN = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");

    private final SubscriptionTransactionService transactionService = mock(SubscriptionTransactionService.class);
    private final UserContextService userContextService = mock(UserContextService.class);
    private final AppUserService appUserService = mock(AppUserService.class);
    private final Environment environment = mock(Environment.class);
    private final SubscriptionEntitlementService service = new SubscriptionEntitlementService(
            transactionService,
            userContextService,
            appUserService,
            environment
    );

    @Test
    void verifiedClientSyncAcceptsEntitlementBoundToAuthenticatedAccount() {
        JWSTransactionDecodedPayload payload = transaction(USER_TOKEN);
        SubscriptionStatusResponse expected = new SubscriptionStatusResponse(
                PlanType.PLUS,
                "watchmyai.plus.monthly",
                true
        );
        when(userContextService.getCurrentUser()).thenReturn(new UserIdentity(USER_ID, USER_TOKEN.toString()));
        when(transactionService.findByOriginalTransactionId(ORIGINAL_TRANSACTION_ID)).thenReturn(Optional.empty());
        when(transactionService.processTransaction(USER_ID, payload, "app_store_server_library", null, null, null))
                .thenReturn(expected);

        SubscriptionStatusResponse response = service.syncFromClient(
                syncRequest(),
                AppStoreServerService.VerificationResult.verified(payload)
        );

        assertThat(response).isSameAs(expected);
    }

    @Test
    void verifiedClientSyncRejectsEntitlementBoundToAnotherAccount() {
        UUID anotherToken = UUID.fromString("9f9d51bc-70ef-31ca-9c14-f307980a29d8");
        JWSTransactionDecodedPayload payload = transaction(anotherToken);
        when(userContextService.getCurrentUser()).thenReturn(new UserIdentity(USER_ID, USER_TOKEN.toString()));
        when(transactionService.findByOriginalTransactionId(ORIGINAL_TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncFromClient(
                syncRequest(),
                AppStoreServerService.VerificationResult.verified(payload)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("App Store transaction is not associated with the authenticated account.");

        verify(transactionService, never()).processTransaction(anyString(), any(), anyString(), any(), any(), any());
    }

    @Test
    void verifiedClientSyncRejectsPreviouslyRecordedEntitlementOwnedByAnotherUser() {
        JWSTransactionDecodedPayload payload = transaction(USER_TOKEN);
        AppStoreSubscriptionEntity existing = new AppStoreSubscriptionEntity("apple:user-2", ORIGINAL_TRANSACTION_ID);
        when(userContextService.getCurrentUser()).thenReturn(new UserIdentity(USER_ID, USER_TOKEN.toString()));
        when(transactionService.findByOriginalTransactionId(ORIGINAL_TRANSACTION_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.syncFromClient(
                syncRequest(),
                AppStoreServerService.VerificationResult.verified(payload)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("App Store transaction is associated with a different account.");
    }

    @Test
    void verifiedClientSyncAllowsExistingTokenlessLegacyEntitlementForRecordedOwner() {
        JWSTransactionDecodedPayload payload = transaction(null);
        AppStoreSubscriptionEntity existing = new AppStoreSubscriptionEntity(USER_ID, ORIGINAL_TRANSACTION_ID);
        SubscriptionStatusResponse expected = new SubscriptionStatusResponse(
                PlanType.PLUS,
                "watchmyai.plus.monthly",
                true
        );
        when(userContextService.getCurrentUser()).thenReturn(new UserIdentity(USER_ID, USER_TOKEN.toString()));
        when(transactionService.findByOriginalTransactionId(ORIGINAL_TRANSACTION_ID)).thenReturn(Optional.of(existing));
        when(transactionService.processTransaction(USER_ID, payload, "app_store_server_library", null, null, null))
                .thenReturn(expected);

        SubscriptionStatusResponse response = service.syncFromClient(
                syncRequest(),
                AppStoreServerService.VerificationResult.verified(payload)
        );

        assertThat(response).isSameAs(expected);
    }

    private JWSTransactionDecodedPayload transaction(UUID appAccountToken) {
        JWSTransactionDecodedPayload payload = mock(JWSTransactionDecodedPayload.class);
        when(payload.getOriginalTransactionId()).thenReturn(ORIGINAL_TRANSACTION_ID);
        when(payload.getAppAccountToken()).thenReturn(appAccountToken);
        return payload;
    }

    private SubscriptionSyncRequest syncRequest() {
        return new SubscriptionSyncRequest(
                "watchmyai.plus.monthly",
                "transaction-1",
                ORIGINAL_TRANSACTION_ID,
                "PRODUCTION",
                "header.payload.signature",
                null,
                USER_TOKEN.toString()
        );
    }
}
