package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;

import java.time.Instant;
import java.util.UUID;

/**
 * Value object carrying all fields needed to update an {@link AppStoreSubscriptionEntity}.
 * Replaces the previous 16-parameter {@code update()} method.
 */
record SubscriptionUpdatePayload(
        String transactionId,
        String productId,
        PlanType planType,
        String environment,
        UUID appAccountToken,
        String status,
        boolean active,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason,
        boolean gracePeriod,
        boolean billingRetry,
        String verificationSource,
        String lastNotificationType,
        String lastNotificationSubtype,
        Instant lastVerifiedAt
) {}
