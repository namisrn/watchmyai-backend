package com.watchmyai.user;

import com.watchmyai.ai.AiRequestLogRepository;
import com.watchmyai.quota.UserPlanRepository;
import com.watchmyai.quota.UserUsageRepository;
import com.watchmyai.subscription.AppStoreSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfers everything a device accumulated as a guest (plan, current usage, subscriptions, AI
 * history, the attested device record) onto the account when the user signs in with Apple for the
 * first time. Triggered from {@link AuthController#apple}; the guest session is dropped afterwards.
 */
@Service
public class GuestMigrationService {

    private static final Logger log = LoggerFactory.getLogger(GuestMigrationService.class);
    private static final String GUEST_PREFIX = "guest:";

    private final UserPlanRepository userPlanRepository;
    private final UserUsageRepository userUsageRepository;
    private final AppStoreSubscriptionRepository appStoreSubscriptionRepository;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final DeviceAttestationRepository deviceAttestationRepository;
    private final UserSessionRepository userSessionRepository;

    public GuestMigrationService(
            UserPlanRepository userPlanRepository,
            UserUsageRepository userUsageRepository,
            AppStoreSubscriptionRepository appStoreSubscriptionRepository,
            AiRequestLogRepository aiRequestLogRepository,
            DeviceAttestationRepository deviceAttestationRepository,
            UserSessionRepository userSessionRepository
    ) {
        this.userPlanRepository = userPlanRepository;
        this.userUsageRepository = userUsageRepository;
        this.appStoreSubscriptionRepository = appStoreSubscriptionRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.deviceAttestationRepository = deviceAttestationRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Transactional
    public void migrateGuestToAccount(String guestUserId, String accountUserId) {
        if (guestUserId == null || accountUserId == null
                || !guestUserId.startsWith(GUEST_PREFIX)
                || guestUserId.equals(accountUserId)) {
            return;
        }

        // Plan + current-period usage are unique per user: move them only when the account has
        // none yet (the common first-sign-in case); otherwise the account's own rows win and the
        // guest copies are dropped to avoid a unique-constraint collision.
        if (userPlanRepository.existsByUserId(accountUserId)) {
            userPlanRepository.deleteByUserId(guestUserId);
        } else {
            userPlanRepository.reassignUser(guestUserId, accountUserId);
        }

        if (userUsageRepository.existsByUserId(accountUserId)) {
            userUsageRepository.deleteByUserId(guestUserId);
        } else {
            userUsageRepository.reassignUser(guestUserId, accountUserId);
        }

        // Subscriptions (original_transaction_id is globally unique), AI history and the attested
        // device record can always be repointed without collision.
        appStoreSubscriptionRepository.reassignUser(guestUserId, accountUserId);
        aiRequestLogRepository.reassignUser(guestUserId, accountUserId);
        deviceAttestationRepository.reassignUser(guestUserId, accountUserId);

        // The guest session is now superseded by the account session.
        userSessionRepository.deleteByUserId(guestUserId);

        log.info("Migrated guest data to account guest={} account={}", guestUserId, accountUserId);
    }
}
