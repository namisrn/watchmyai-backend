package com.watchmyai.subscription;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.watchmyai.user.AppUserService;
import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEntitlementService.class);

    private final SubscriptionTransactionService transactionService;
    private final UserContextService userContextService;
    private final AppUserService appUserService;
    private final Environment environment;

    public SubscriptionEntitlementService(
            SubscriptionTransactionService transactionService,
            UserContextService userContextService,
            AppUserService appUserService,
            Environment environment
    ) {
        this.transactionService = transactionService;
        this.userContextService = userContextService;
        this.appUserService = appUserService;
        this.environment = environment;
    }

    @Transactional
    public SubscriptionStatusResponse syncFromClient(
            SubscriptionSyncRequest request,
            AppStoreServerService.VerificationResult verificationResult
    ) {
        UserIdentity currentUser = userContextService.getCurrentUser();
        String userId = currentUser.userId();

        if (verificationResult.verified()) {
            validateClientEntitlementOwner(currentUser, verificationResult.payload());
            return transactionService.processTransaction(
                    userId,
                    verificationResult.payload(),
                    verificationResult.verificationSource(),
                    null,
                    null,
                    null
            );
        }

        if (!isDevelopmentProfile()) {
            throw new IllegalArgumentException("App Store transaction verification is required.");
        }

        return transactionService.processUnverifiedTransaction(
                userId,
                request,
                verificationResult.verificationSource()
        );
    }

    @Transactional
    public AppStoreNotificationResponse applyNotification(
            ResponseBodyV2DecodedPayload payload,
            JWSTransactionDecodedPayload transaction
    ) {
        if (payload == null || transaction == null) {
            return new AppStoreNotificationResponse(true, "jws_shape_only");
        }

        String originalTransactionId = transaction.getOriginalTransactionId();
        Optional<AppStoreSubscriptionEntity> existing = transactionService
                .findByOriginalTransactionId(originalTransactionId);

        if (existing.isEmpty()) {
            UUID appAccountToken = transaction.getAppAccountToken();
            if (appAccountToken == null) {
                // Orphaned notification: we have no prior record AND no appAccountToken to attribute
                // the transaction to. Log loud so the drift is visible (Apple S2S retries succeed
                // with HTTP 200 but the user's plan state is silently stuck without this log).
                log.warn(
                        "Orphan App Store notification: unknown originalTransactionId={} and no appAccountToken — plan state will not be updated. notificationUUID={} type={} subtype={}",
                        originalTransactionId,
                        payload.getNotificationUUID(),
                        notificationType(payload),
                        notificationSubtype(payload)
                );
                return new AppStoreNotificationResponse(true, "unknown_original_transaction");
            }

            return appUserService
                    .findByAppAccountToken(appAccountToken)
                    .map(user -> {
                        transactionService.processTransaction(
                                user.getUserId(),
                                transaction,
                                "app_store_server_notification",
                                notificationType(payload),
                                notificationSubtype(payload),
                                payload.getData() == null ? null : payload.getData().getStatus()
                        );
                        return new AppStoreNotificationResponse(true, "plan_updated_by_app_account_token");
                    })
                    .orElseGet(() -> {
                        // Token present but no user matches — likely a stale/recycled token, or a
                        // sandbox notification leaking into prod. Loud log so the drift is traceable.
                        log.warn(
                                "Orphan App Store notification: appAccountToken={} resolves to no user. originalTransactionId={} notificationUUID={} type={} subtype={}",
                                appAccountToken,
                                originalTransactionId,
                                payload.getNotificationUUID(),
                                notificationType(payload),
                                notificationSubtype(payload)
                        );
                        return new AppStoreNotificationResponse(true, "unknown_app_account_token");
                    });
        }

        transactionService.processTransaction(
                existing.get().getUserId(),
                transaction,
                "app_store_server_notification",
                notificationType(payload),
                notificationSubtype(payload),
                payload.getData() == null ? null : payload.getData().getStatus()
        );

        return new AppStoreNotificationResponse(true, "plan_updated");
    }

    @Transactional
    public SubscriptionStatusResponse getCurrentStatus() {
        String userId = userContextService.getCurrentUser().userId();
        return transactionService.getActiveStatus(userId);
    }

    private String notificationType(ResponseBodyV2DecodedPayload payload) {
        return transactionService.stringValue(payload.getRawNotificationType(), payload.getNotificationType());
    }

    private String notificationSubtype(ResponseBodyV2DecodedPayload payload) {
        return transactionService.stringValue(payload.getRawSubtype(), payload.getSubtype());
    }

    private void validateClientEntitlementOwner(UserIdentity currentUser, JWSTransactionDecodedPayload transaction) {
        Optional<AppStoreSubscriptionEntity> existing = transactionService
                .findByOriginalTransactionId(transaction.getOriginalTransactionId());

        if (existing.isPresent() && !existing.get().getUserId().equals(currentUser.userId())) {
            throw new IllegalArgumentException("App Store transaction is associated with a different account.");
        }

        UUID transactionToken = transaction.getAppAccountToken();
        UUID authenticatedUserToken = parseAppAccountToken(currentUser.appAccountToken());
        if (transactionToken != null && transactionToken.equals(authenticatedUserToken)) {
            return;
        }

        // Existing tokenless transactions may have been purchased before appAccountToken
        // was introduced. They remain restorable only to their already recorded owner.
        if (transactionToken == null
                && existing.isPresent()
                && existing.get().getUserId().equals(currentUser.userId())
                && existing.get().getAppAccountToken() == null) {
            return;
        }

        throw new IllegalArgumentException("App Store transaction is not associated with the authenticated account.");
    }

    private UUID parseAppAccountToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException invalidToken) {
            return null;
        }
    }

    private boolean isDevelopmentProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test");
    }
}
