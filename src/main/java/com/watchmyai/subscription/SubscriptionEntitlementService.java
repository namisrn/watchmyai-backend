package com.watchmyai.subscription;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.model.Status;
import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UsageService;
import com.watchmyai.quota.UserPlanService;
import com.watchmyai.user.AppUserService;
import com.watchmyai.user.UserContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEntitlementService.class);

    private final AppStoreSubscriptionRepository subscriptionRepository;
    private final SubscriptionProductCatalog productCatalog;
    private final UserPlanService userPlanService;
    private final UsageService usageService;
    private final UserContextService userContextService;
    private final AppUserService appUserService;
    private final Environment environment;
    private final Clock clock;

    public SubscriptionEntitlementService(
            AppStoreSubscriptionRepository subscriptionRepository,
            SubscriptionProductCatalog productCatalog,
            UserPlanService userPlanService,
            UsageService usageService,
            UserContextService userContextService,
            AppUserService appUserService,
            Environment environment,
            Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.productCatalog = productCatalog;
        this.userPlanService = userPlanService;
        this.usageService = usageService;
        this.userContextService = userContextService;
        this.appUserService = appUserService;
        this.environment = environment;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionStatusResponse syncFromClient(
            SubscriptionSyncRequest request,
            AppStoreServerService.VerificationResult verificationResult
    ) {
        String userId = userContextService.getCurrentUser().userId();

        if (verificationResult.verified()) {
            return upsertFromTransaction(
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

        PlanType planType = productCatalog
                .findPlanType(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription product."));

        AppStoreSubscriptionEntity subscription = subscriptionRepository
                .findByOriginalTransactionId(request.originalTransactionId())
                .orElseGet(() -> new AppStoreSubscriptionEntity(userId, request.originalTransactionId()));

        Instant now = Instant.now(clock);
        subscription.update(
                request.transactionId(),
                request.productId(),
                planType,
                request.environment(),
                parseAppAccountToken(request.appAccountToken()),
                "UNVERIFIED_ACTIVE",
                true,
                null,
                null,
                null,
                false,
                false,
                verificationResult.verificationSource(),
                null,
                null,
                now
        );

        subscriptionRepository.save(subscription);
        refreshUserPlan(userId);
        return toResponse(subscription);
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
        Optional<AppStoreSubscriptionEntity> existing = subscriptionRepository
                .findByOriginalTransactionId(originalTransactionId);

        if (existing.isEmpty()) {
            UUID appAccountToken = transaction.getAppAccountToken();
            if (appAccountToken == null) {
                return new AppStoreNotificationResponse(true, "unknown_original_transaction");
            }

            return appUserService
                    .findByAppAccountToken(appAccountToken)
                    .map(user -> {
                        upsertFromTransaction(
                                user.getUserId(),
                                transaction,
                                "app_store_server_notification",
                                stringValue(payload.getRawNotificationType(), payload.getNotificationType()),
                                stringValue(payload.getRawSubtype(), payload.getSubtype()),
                                payload.getData() == null ? null : payload.getData().getStatus()
                        );
                        return new AppStoreNotificationResponse(true, "plan_updated_by_app_account_token");
                    })
                    .orElseGet(() -> new AppStoreNotificationResponse(true, "unknown_app_account_token"));
        }

        AppStoreSubscriptionEntity subscription = existing.get();
        upsertFromTransaction(
                subscription.getUserId(),
                transaction,
                "app_store_server_notification",
                stringValue(payload.getRawNotificationType(), payload.getNotificationType()),
                stringValue(payload.getRawSubtype(), payload.getSubtype()),
                payload.getData() == null ? null : payload.getData().getStatus()
        );

        return new AppStoreNotificationResponse(true, "plan_updated");
    }

    @Transactional
    public SubscriptionStatusResponse getCurrentStatus() {
        String userId = userContextService.getCurrentUser().userId();
        Instant now = Instant.now(clock);
        subscriptionRepository
                .findByUserIdAndActiveTrue(userId)
                .stream()
                .filter(subscription -> subscription.getExpiresAt() != null && !subscription.getExpiresAt().isAfter(now))
                .forEach(subscription -> subscription.deactivate("EXPIRED", now));

        refreshUserPlan(userId);

        return subscriptionRepository
                .findByUserIdAndActiveTrue(userId)
                .stream()
                .max(Comparator.comparing(subscription -> subscription.getPlanType().ordinal()))
                .map(this::toResponse)
                .orElseGet(() -> new SubscriptionStatusResponse(PlanType.FREE, null, true));
    }

    private SubscriptionStatusResponse upsertFromTransaction(
            String userId,
            JWSTransactionDecodedPayload payload,
            String verificationSource,
            String notificationType,
            String notificationSubtype,
            Status appStoreStatus
    ) {
        String productId = payload.getProductId();
        PlanType planType = productCatalog
                .findPlanType(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription product."));

        String originalTransactionId = payload.getOriginalTransactionId();
        AppStoreSubscriptionEntity subscription = subscriptionRepository
                .findByOriginalTransactionId(originalTransactionId)
                .orElseGet(() -> new AppStoreSubscriptionEntity(userId, originalTransactionId));

        Instant now = Instant.now(clock);
        Instant expiresAt = toInstant(payload.getExpiresDate());
        Instant revokedAt = toInstant(payload.getRevocationDate());
        boolean billingRetry = appStoreStatus == Status.BILLING_RETRY;
        boolean gracePeriod = appStoreStatus == Status.BILLING_GRACE_PERIOD;
        boolean active = revokedAt == null
                && !Boolean.TRUE.equals(payload.getIsUpgraded())
                && (expiresAt == null || expiresAt.isAfter(now))
                && appStoreStatus != Status.EXPIRED
                && appStoreStatus != Status.REVOKED;

        subscription.update(
                payload.getTransactionId(),
                productId,
                planType,
                stringValue(payload.getRawEnvironment(), payload.getEnvironment()),
                payload.getAppAccountToken(),
                appStoreStatus == null ? (active ? "ACTIVE" : "INACTIVE") : appStoreStatus.name(),
                active,
                expiresAt,
                revokedAt,
                payload.getRawRevocationReason() == null ? null : payload.getRawRevocationReason().toString(),
                gracePeriod,
                billingRetry,
                verificationSource,
                notificationType,
                notificationSubtype,
                now
        );

        subscriptionRepository.save(subscription);
        refreshUserPlan(userId);
        return toResponse(subscription);
    }

    private void refreshUserPlan(String userId) {
        PlanType activePlan = subscriptionRepository
                .findByUserIdAndActiveTrue(userId)
                .stream()
                .map(AppStoreSubscriptionEntity::getPlanType)
                .max(Comparator.comparing(Enum::ordinal))
                .orElse(PlanType.FREE);

        PlanType previousPlan = userPlanService.getPlanForUser(userId);
        userPlanService.setCurrentPlanForUser(userId, activePlan);

        // Plan-downgrade quota reset: when the user drops to a lower plan (e.g. Plus → Free
        // at end-of-billing-period), their prior usage on the higher plan would otherwise
        // exceed the new lower monthly limit and immediately block them with "Limit reached"
        // on the very next request. Reset the current period's counters so the new plan
        // starts with a fresh budget. The lifetime counter + EUR cost are preserved.
        if (activePlan.ordinal() < previousPlan.ordinal()) {
            log.info(
                    "Plan downgrade detected userId={} {} -> {} — resetting current period's usage counters",
                    userId, previousPlan, activePlan
            );
            usageService.resetUsageForPlanDowngrade(userId, activePlan);
        }
    }

    private SubscriptionStatusResponse toResponse(AppStoreSubscriptionEntity subscription) {
        return new SubscriptionStatusResponse(
                subscription.getPlanType(),
                subscription.getProductId(),
                subscription.isActive() && isServerVerified(subscription),
                subscription.getVerificationSource(),
                subscription.getTransactionId(),
                subscription.getOriginalTransactionId(),
                subscription.getEnvironment(),
                subscription.getExpiresAt(),
                subscription.getRevokedAt(),
                subscription.getStatus(),
                subscription.getAppAccountToken()
        );
    }

    private boolean isServerVerified(AppStoreSubscriptionEntity subscription) {
        return "app_store_server_library".equals(subscription.getVerificationSource())
                || "app_store_server_notification".equals(subscription.getVerificationSource());
    }

    private Instant toInstant(Long milliseconds) {
        return milliseconds == null ? null : Instant.ofEpochMilli(milliseconds);
    }

    private String stringValue(String rawValue, Object typedValue) {
        if (rawValue != null && !rawValue.isBlank()) {
            return rawValue;
        }
        return typedValue == null ? null : typedValue.toString();
    }

    private UUID parseAppAccountToken(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private boolean isDevelopmentProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test");
    }
}
