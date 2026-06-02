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
        Instant lastVerifiedAt,
        // Renewal-Info aus der S2S-Notification; null = unbekannt (z. B. Client-Sync
        // ohne Renewal-JWS). Bei null lässt die Entity die bestehenden Werte stehen,
        // damit ein Client-Sync die per Notification gesetzte Info nicht überschreibt.
        Boolean autoRenewStatus,
        String autoRenewProductId
) {}
