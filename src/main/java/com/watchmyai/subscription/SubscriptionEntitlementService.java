package com.watchmyai.subscription;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.watchmyai.user.AppUserService;
import com.watchmyai.user.UserContextService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionEntitlementService {

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
        String userId = userContextService.getCurrentUser().userId();

        if (verificationResult.verified()) {
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
                    .orElseGet(() -> new AppStoreNotificationResponse(true, "unknown_app_account_token"));
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

    private boolean isDevelopmentProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test");
    }
}
