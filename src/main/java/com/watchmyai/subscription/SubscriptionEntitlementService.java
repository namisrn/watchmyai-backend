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
    private final AppStoreServerService appStoreServerService;
    private final Environment environment;

    public SubscriptionEntitlementService(
            SubscriptionTransactionService transactionService,
            UserContextService userContextService,
            AppUserService appUserService,
            AppStoreServerService appStoreServerService,
            Environment environment
    ) {
        this.transactionService = transactionService;
        this.userContextService = userContextService;
        this.appUserService = appUserService;
        this.appStoreServerService = appStoreServerService;
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
            // Client-Sync trägt keine signierte Renewal-Info → null/null. Die
            // konditionale Entity-Merge-Logik bewahrt eine zuvor per S2S-Notification
            // gesetzte Renewal-Info, statt sie mit null zu überschreiben.
            return transactionService.processTransaction(
                    userId,
                    verificationResult.payload(),
                    verificationResult.verificationSource(),
                    null,
                    null,
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

        // Renewal-Info (auto-renew Status + nächste Produkt-ID) aus der Notification.
        // Best-effort: bleibt null/null, wenn die Notification keine Renewal-Info trägt
        // oder die Verifikation fehlschlägt — die Plan-Aktualisierung läuft trotzdem.
        RenewalInfo renewal = extractRenewalInfo(payload);

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
                                payload.getData() == null ? null : payload.getData().getStatus(),
                                renewal.autoRenewStatus(),
                                renewal.autoRenewProductId()
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
                payload.getData() == null ? null : payload.getData().getStatus(),
                renewal.autoRenewStatus(),
                renewal.autoRenewProductId()
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

        if (existing.isPresent()) {
            // Ownership is already established. Reject only when the transaction belongs to a
            // *different* account; otherwise accept regardless of the embedded appAccountToken.
            // This covers two legitimate cases without weakening the cross-account guard:
            //   1. guest→account migration — the transaction keeps its original (guest)
            //      appAccountToken, but the subscription row is now owned by the signed-in account;
            //   2. legacy tokenless purchases recorded before appAccountToken existed.
            // A record can only exist under a user after a prior sync passed the first-sync gate
            // below (token match) or was migrated in, so "already mine" is safe to accept.
            if (!existing.get().getUserId().equals(currentUser.userId())) {
                throw new IllegalArgumentException("App Store transaction is associated with a different account.");
            }
            return;
        }

        // First sync of this transaction: bind it to the purchaser via the Apple-signed
        // appAccountToken. Accounts carry the AppUser token; guests carry the deterministic
        // guest token (see AppSessionService#guestAppAccountToken).
        UUID transactionToken = transaction.getAppAccountToken();
        UUID authenticatedUserToken = parseAppAccountToken(currentUser.appAccountToken());
        if (transactionToken != null && transactionToken.equals(authenticatedUserToken)) {
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

    /**
     * Extrahiert auto-renew Status + nächste Produkt-ID aus der signierten Renewal-Info
     * der Notification. Verifikation läuft in {@link AppStoreServerService#decodeRenewalInfo};
     * jeder Fehlschlag degradiert zu „unbekannt" (null/null), damit die Plan-Aktualisierung
     * niemals an fehlender/ungültiger Renewal-Info scheitert.
     */
    private RenewalInfo extractRenewalInfo(ResponseBodyV2DecodedPayload payload) {
        if (payload.getData() == null || payload.getData().getSignedRenewalInfo() == null) {
            return RenewalInfo.unknown();
        }
        return appStoreServerService.decodeRenewalInfo(payload.getData().getSignedRenewalInfo())
                .map(info -> new RenewalInfo(
                        info.getRawAutoRenewStatus() == null ? null : info.getRawAutoRenewStatus() == 1,
                        info.getAutoRenewProductId()
                ))
                .orElse(RenewalInfo.unknown());
    }

    private record RenewalInfo(Boolean autoRenewStatus, String autoRenewProductId) {
        static RenewalInfo unknown() {
            return new RenewalInfo(null, null);
        }
    }
}
