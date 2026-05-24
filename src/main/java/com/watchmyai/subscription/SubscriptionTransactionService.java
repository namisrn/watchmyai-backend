package com.watchmyai.subscription;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.Status;
import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.UserPlanService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Core subscription-transaction processing shared between client sync and server notification
 * flows. Owns upsert coordination, plan refresh, and response building so neither
 * {@link SubscriptionEntitlementService} nor {@link SubscriptionNotificationHandler} duplicate
 * this logic.
 */
@Service
public class SubscriptionTransactionService {

    private final AppStoreSubscriptionRepository subscriptionRepository;
    private final SubscriptionUpsertService subscriptionUpsertService;
    private final SubscriptionProductCatalog productCatalog;
    private final UserPlanService userPlanService;
    private final Clock clock;

    public SubscriptionTransactionService(
            AppStoreSubscriptionRepository subscriptionRepository,
            SubscriptionUpsertService subscriptionUpsertService,
            SubscriptionProductCatalog productCatalog,
            UserPlanService userPlanService,
            Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionUpsertService = subscriptionUpsertService;
        this.productCatalog = productCatalog;
        this.userPlanService = userPlanService;
        this.clock = clock;
    }

    Optional<AppStoreSubscriptionEntity> findByOriginalTransactionId(String originalTransactionId) {
        return subscriptionRepository.findByOriginalTransactionId(originalTransactionId);
    }

    /**
     * Upserts a subscription from a verified JWS transaction payload and refreshes the user plan.
     * Returns the resulting subscription status.
     */
    @Transactional
    public SubscriptionStatusResponse processTransaction(
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

        SubscriptionUpdatePayload update = new SubscriptionUpdatePayload(
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
        persistSubscription(userId, originalTransactionId, subscription -> subscription.update(update));

        PlanType resultingPlan = refreshUserPlan(userId);
        return buildActiveStatus(userId, resultingPlan);
    }

    /**
     * Upserts a subscription from an unverified dev/test request (no JWS payload available).
     */
    @Transactional
    public SubscriptionStatusResponse processUnverifiedTransaction(
            String userId,
            SubscriptionSyncRequest request,
            String verificationSource
    ) {
        PlanType planType = productCatalog
                .findPlanType(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription product."));

        Instant now = Instant.now(clock);
        SubscriptionUpdatePayload update = new SubscriptionUpdatePayload(
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
                verificationSource,
                null,
                null,
                now
        );
        persistSubscription(userId, request.originalTransactionId(), subscription -> subscription.update(update));

        PlanType resultingPlan = refreshUserPlan(userId);
        return buildActiveStatus(userId, resultingPlan);
    }

    /**
     * Expires locally stale subscriptions, refreshes the user plan, and returns the current status.
     */
    @Transactional
    public SubscriptionStatusResponse getActiveStatus(String userId) {
        Instant now = Instant.now(clock);
        subscriptionRepository
                .findByUserIdAndActiveTrue(userId)
                .stream()
                .filter(s -> s.getExpiresAt() != null && !s.getExpiresAt().isAfter(now))
                .forEach(s -> s.deactivate("EXPIRED", now));

        refreshUserPlan(userId);

        return subscriptionRepository
                .findByUserIdAndActiveTrue(userId)
                .stream()
                .max(Comparator.comparing(s -> s.getPlanType().ordinal()))
                .map(this::toResponse)
                .orElseGet(() -> new SubscriptionStatusResponse(PlanType.FREE, null, true));
    }

    PlanType refreshUserPlan(String userId) {
        PlanType activePlan = subscriptionRepository
                .findByUserIdAndActiveTrue(userId)
                .stream()
                .map(AppStoreSubscriptionEntity::getPlanType)
                .max(Comparator.comparing(Enum::ordinal))
                .orElse(PlanType.FREE);

        userPlanService.setCurrentPlanForUser(userId, activePlan);
        return activePlan;
    }

    SubscriptionStatusResponse buildActiveStatus(String userId, PlanType planType) {
        return subscriptionRepository
                .findByUserIdAndActiveTrue(userId)
                .stream()
                .filter(subscription -> subscription.getPlanType() == planType)
                .max(Comparator.comparing(
                        AppStoreSubscriptionEntity::getLastVerifiedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(this::toResponse)
                .orElseGet(() -> new SubscriptionStatusResponse(PlanType.FREE, null, true));
    }

    SubscriptionStatusResponse toResponse(AppStoreSubscriptionEntity subscription) {
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

    private void persistSubscription(
            String userId,
            String originalTransactionId,
            Consumer<AppStoreSubscriptionEntity> applyUpdate
    ) {
        try {
            subscriptionUpsertService.upsert(userId, originalTransactionId, applyUpdate);
        } catch (DataIntegrityViolationException concurrentInsert) {
            subscriptionUpsertService.upsert(userId, originalTransactionId, applyUpdate);
        }
    }

    private boolean isServerVerified(AppStoreSubscriptionEntity subscription) {
        return "app_store_server_library".equals(subscription.getVerificationSource())
                || "app_store_server_notification".equals(subscription.getVerificationSource());
    }

    private Instant toInstant(Long milliseconds) {
        return milliseconds == null ? null : Instant.ofEpochMilli(milliseconds);
    }

    String stringValue(String rawValue, Object typedValue) {
        if (rawValue != null && !rawValue.isBlank()) {
            return rawValue;
        }
        return typedValue == null ? null : typedValue.toString();
    }

    private UUID parseAppAccountToken(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }
}
