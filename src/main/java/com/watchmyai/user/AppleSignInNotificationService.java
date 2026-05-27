package com.watchmyai.user;

import com.watchmyai.ai.AiRequestLogRepository;
import com.watchmyai.quota.UserPlanRepository;
import com.watchmyai.quota.UserUsageRepository;
import com.watchmyai.subscription.AppStoreSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Reacts to verified Apple Sign-In server-to-server notifications. Hard events
 * ({@code consent-revoked}, {@code account-delete}) trigger a full server-side
 * purge of the user account and all derived data. Soft events ({@code email-*})
 * update the cached email address only — the account stays alive.
 *
 * <p>Unlike user-initiated deletion in {@link AccountDeletionService}, we don't
 * call Apple's {@code /auth/revoke} endpoint here — Apple already knows the
 * relationship is over, and a revoke call would echo a notification right back
 * (loop risk).
 */
@Service
public class AppleSignInNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppleSignInNotificationService.class);

    private final AppUserRepository appUserRepository;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final AppStoreSubscriptionRepository appStoreSubscriptionRepository;
    private final UserUsageRepository userUsageRepository;
    private final UserPlanRepository userPlanRepository;
    private final UserSessionRepository userSessionRepository;

    public AppleSignInNotificationService(
            AppUserRepository appUserRepository,
            AiRequestLogRepository aiRequestLogRepository,
            AppStoreSubscriptionRepository appStoreSubscriptionRepository,
            UserUsageRepository userUsageRepository,
            UserPlanRepository userPlanRepository,
            UserSessionRepository userSessionRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.appStoreSubscriptionRepository = appStoreSubscriptionRepository;
        this.userUsageRepository = userUsageRepository;
        this.userPlanRepository = userPlanRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Transactional
    public void handle(AppleSignInNotificationEvent event) {
        Optional<AppUserEntity> user = appUserRepository.findByAppleSubject(event.subject());

        if (user.isEmpty()) {
            // Apple delivers notifications best-effort, sometimes for users we've
            // never seen (Apple-id-only state from a different test app, etc.).
            // Always log so an operator can correlate, but never throw — Apple
            // retries on non-2xx and would spam the endpoint forever.
            log.warn("Apple Sign-In notification for unknown subject type={} sub={}",
                    event.type(), event.subject());
            return;
        }

        AppUserEntity entity = user.get();
        String userId = entity.getUserId();

        switch (event.type()) {
            case CONSENT_REVOKED, ACCOUNT_DELETE -> {
                log.info("Apple Sign-In notification type={} sub={} userId={} → purging account",
                        event.type(), event.subject(), userId);
                purgeAccount(userId);
            }
            case EMAIL_DISABLED, EMAIL_ENABLED -> {
                // Soft event — only refresh the cached email if Apple included one.
                // We don't try to "disable" anything ourselves; the next sign-in
                // round-trip will pick up the new private-email proxy if Apple
                // rotates it.
                if (event.email() != null && !event.email().isBlank()) {
                    // updateAppleProfile's guard skips blank values, so passing
                    // null for appleUserId leaves the stored copy untouched.
                    entity.updateAppleProfile(null, event.email());
                    appUserRepository.save(entity);
                }
                log.info("Apple Sign-In notification type={} sub={} userId={} → email cache updated",
                        event.type(), event.subject(), userId);
            }
            case UNKNOWN -> log.warn(
                    "Apple Sign-In notification with unknown type sub={} userId={} — ignoring",
                    event.subject(), userId
            );
        }
    }

    /**
     * Mirrors {@link AccountDeletionService#deleteAccount(String, String)} minus
     * the Apple-side revoke call — at notification time Apple already considers
     * the consent revoked, and a re-revoke would echo another notification.
     */
    private void purgeAccount(String userId) {
        aiRequestLogRepository.deleteByUserId(userId);
        appStoreSubscriptionRepository.deleteByUserId(userId);
        userUsageRepository.deleteByUserId(userId);
        userPlanRepository.deleteByUserId(userId);
        userSessionRepository.deleteByUserId(userId);
        appUserRepository.deleteByUserId(userId);
    }
}
