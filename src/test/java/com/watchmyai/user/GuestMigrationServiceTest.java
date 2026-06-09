package com.watchmyai.user;

import com.watchmyai.ai.AiRequestLogRepository;
import com.watchmyai.quota.UserPlanRepository;
import com.watchmyai.quota.UserUsageRepository;
import com.watchmyai.subscription.AppStoreSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestMigrationServiceTest {

    private static final String GUEST = "guest:abc";
    private static final String ACCOUNT = "apple:sub";

    @Mock private UserPlanRepository userPlanRepository;
    @Mock private UserUsageRepository userUsageRepository;
    @Mock private AppStoreSubscriptionRepository appStoreSubscriptionRepository;
    @Mock private AiRequestLogRepository aiRequestLogRepository;
    @Mock private DeviceAttestationRepository deviceAttestationRepository;
    @Mock private UserSessionRepository userSessionRepository;

    @InjectMocks private GuestMigrationService service;

    @Test
    void reassignsRowsWhenAccountHasNone() {
        when(userPlanRepository.existsByUserId(ACCOUNT)).thenReturn(false);
        when(userUsageRepository.existsByUserId(ACCOUNT)).thenReturn(false);

        service.migrateGuestToAccount(GUEST, ACCOUNT);

        verify(userPlanRepository).reassignUser(GUEST, ACCOUNT);
        verify(userPlanRepository, never()).deleteByUserId(GUEST);
        verify(userUsageRepository).reassignUser(GUEST, ACCOUNT);
        verify(userUsageRepository, never()).deleteByUserId(GUEST);
        verify(appStoreSubscriptionRepository).reassignUser(GUEST, ACCOUNT);
        verify(aiRequestLogRepository).reassignUser(GUEST, ACCOUNT);
        verify(deviceAttestationRepository).reassignUser(GUEST, ACCOUNT);
        verify(userSessionRepository).deleteByUserId(GUEST);
    }

    @Test
    void dropsGuestPlanAndUsageWhenAccountAlreadyHasThem() {
        when(userPlanRepository.existsByUserId(ACCOUNT)).thenReturn(true);
        when(userUsageRepository.existsByUserId(ACCOUNT)).thenReturn(true);

        service.migrateGuestToAccount(GUEST, ACCOUNT);

        verify(userPlanRepository).deleteByUserId(GUEST);
        verify(userPlanRepository, never()).reassignUser(anyString(), anyString());
        verify(userUsageRepository).deleteByUserId(GUEST);
        verify(userUsageRepository, never()).reassignUser(anyString(), anyString());
        // History / subscriptions / device are always repointed; the guest session is dropped.
        verify(appStoreSubscriptionRepository).reassignUser(GUEST, ACCOUNT);
        verify(aiRequestLogRepository).reassignUser(GUEST, ACCOUNT);
        verify(deviceAttestationRepository).reassignUser(GUEST, ACCOUNT);
        verify(userSessionRepository).deleteByUserId(GUEST);
    }

    @Test
    void noOpForNonGuestIdOrSameId() {
        service.migrateGuestToAccount("apple:other", ACCOUNT); // not a guest id
        service.migrateGuestToAccount(GUEST, GUEST);           // defensive: same id
        service.migrateGuestToAccount(null, ACCOUNT);

        verifyNoInteractions(
                userPlanRepository, userUsageRepository, appStoreSubscriptionRepository,
                aiRequestLogRepository, deviceAttestationRepository, userSessionRepository
        );
    }
}
