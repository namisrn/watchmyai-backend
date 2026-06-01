package com.watchmyai.user;

import com.watchmyai.ai.AiRequestLogRepository;
import com.watchmyai.quota.UserPlanRepository;
import com.watchmyai.quota.UserUsageRepository;
import com.watchmyai.subscription.AppStoreSubscriptionRepository;
import com.watchmyai.telemetry.TelemetryEventRepository;
import com.watchmyai.telemetry.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final AppleSignInTokenRevocationService appleSignInTokenRevocationService;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final AppStoreSubscriptionRepository appStoreSubscriptionRepository;
    private final UserUsageRepository userUsageRepository;
    private final UserPlanRepository userPlanRepository;
    private final UserSessionRepository userSessionRepository;
    private final AppUserRepository appUserRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final TelemetryService telemetryService;

    public AccountDeletionService(
            AppleSignInTokenRevocationService appleSignInTokenRevocationService,
            AiRequestLogRepository aiRequestLogRepository,
            AppStoreSubscriptionRepository appStoreSubscriptionRepository,
            UserUsageRepository userUsageRepository,
            UserPlanRepository userPlanRepository,
            UserSessionRepository userSessionRepository,
            AppUserRepository appUserRepository,
            TelemetryEventRepository telemetryEventRepository,
            TelemetryService telemetryService
    ) {
        this.appleSignInTokenRevocationService = appleSignInTokenRevocationService;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.appStoreSubscriptionRepository = appStoreSubscriptionRepository;
        this.userUsageRepository = userUsageRepository;
        this.userPlanRepository = userPlanRepository;
        this.userSessionRepository = userSessionRepository;
        this.appUserRepository = appUserRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.telemetryService = telemetryService;
    }

    @Transactional
    public void deleteAccount(String userId, String authorizationCode) {
        // Do not erase the server-side identity unless Apple authorization revocation
        // succeeded. A failed reauthentication can then be retried safely by the user.
        appleSignInTokenRevocationService.revokeAuthorization(authorizationCode);

        aiRequestLogRepository.deleteByUserId(userId);
        // Telemetrie ist über den gehashten user_id_hash gespeichert, nicht über die
        // rohe userId — wir bilden hier denselben Hash wie beim Schreiben (siehe
        // TelemetryService) und löschen darüber. Erfüllt Art. 17 DSGVO auch für die
        // pseudonymisierten Analytics-Events.
        telemetryEventRepository.deleteByUserIdHash(telemetryService.hashUserIdForDeletion(userId));
        appStoreSubscriptionRepository.deleteByUserId(userId);
        userUsageRepository.deleteByUserId(userId);
        userPlanRepository.deleteByUserId(userId);
        userSessionRepository.deleteByUserId(userId);
        appUserRepository.deleteByUserId(userId);

        log.info("Deleted account and associated application data userId={}", userId);
    }
}
