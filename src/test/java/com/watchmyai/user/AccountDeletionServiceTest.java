package com.watchmyai.user;

import com.watchmyai.ai.AiRequestLogRepository;
import com.watchmyai.quota.UserPlanRepository;
import com.watchmyai.quota.UserUsageRepository;
import com.watchmyai.subscription.AppStoreSubscriptionRepository;
import com.watchmyai.telemetry.TelemetryEventRepository;
import com.watchmyai.telemetry.TelemetryService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {

    private final AppleSignInTokenRevocationService tokenRevocationService = mock(AppleSignInTokenRevocationService.class);
    private final AiRequestLogRepository aiRequestLogRepository = mock(AiRequestLogRepository.class);
    private final AppStoreSubscriptionRepository appStoreSubscriptionRepository = mock(AppStoreSubscriptionRepository.class);
    private final UserUsageRepository userUsageRepository = mock(UserUsageRepository.class);
    private final UserPlanRepository userPlanRepository = mock(UserPlanRepository.class);
    private final UserSessionRepository userSessionRepository = mock(UserSessionRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final DeviceAttestationRepository deviceAttestationRepository = mock(DeviceAttestationRepository.class);
    private final TelemetryEventRepository telemetryEventRepository = mock(TelemetryEventRepository.class);
    private final TelemetryService telemetryService = mock(TelemetryService.class);

    private final AccountDeletionService service = new AccountDeletionService(
            tokenRevocationService,
            aiRequestLogRepository,
            appStoreSubscriptionRepository,
            userUsageRepository,
            userPlanRepository,
            userSessionRepository,
            appUserRepository,
            deviceAttestationRepository,
            telemetryEventRepository,
            telemetryService
    );

    @Test
    void revokesAppleAccessBeforeDeletingAllAccountData() {
        when(telemetryService.hashUserIdForDeletion("apple:subject-123")).thenReturn("deadbeefdeadbeefdeadbeefdeadbeef");

        service.deleteAccount("apple:subject-123", "fresh-code");

        InOrder inOrder = inOrder(
                tokenRevocationService,
                aiRequestLogRepository,
                telemetryEventRepository,
                appStoreSubscriptionRepository,
                userUsageRepository,
                userPlanRepository,
                userSessionRepository,
                deviceAttestationRepository,
                appUserRepository
        );
        inOrder.verify(tokenRevocationService).revokeAuthorization("fresh-code");
        inOrder.verify(aiRequestLogRepository).deleteByUserId("apple:subject-123");
        inOrder.verify(telemetryEventRepository).deleteByUserIdHash("deadbeefdeadbeefdeadbeefdeadbeef");
        inOrder.verify(appStoreSubscriptionRepository).deleteByUserId("apple:subject-123");
        inOrder.verify(userUsageRepository).deleteByUserId("apple:subject-123");
        inOrder.verify(userPlanRepository).deleteByUserId("apple:subject-123");
        inOrder.verify(userSessionRepository).deleteByUserId("apple:subject-123");
        inOrder.verify(deviceAttestationRepository).deleteByUserId("apple:subject-123");
        inOrder.verify(appUserRepository).deleteByUserId("apple:subject-123");
    }

    @Test
    void keepsAccountDataWhenAppleRevocationFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Apple unavailable"))
                .when(tokenRevocationService).revokeAuthorization("fresh-code");

        assertThatThrownBy(() -> service.deleteAccount("apple:subject-123", "fresh-code"))
                .isInstanceOf(IllegalStateException.class);

        verify(aiRequestLogRepository, never()).deleteByUserId("apple:subject-123");
        verify(telemetryEventRepository, never()).deleteByUserIdHash(org.mockito.ArgumentMatchers.anyString());
        verify(appStoreSubscriptionRepository, never()).deleteByUserId("apple:subject-123");
        verify(userUsageRepository, never()).deleteByUserId("apple:subject-123");
        verify(userPlanRepository, never()).deleteByUserId("apple:subject-123");
        verify(userSessionRepository, never()).deleteByUserId("apple:subject-123");
        verify(deviceAttestationRepository, never()).deleteByUserId("apple:subject-123");
        verify(appUserRepository, never()).deleteByUserId("apple:subject-123");
    }
}
