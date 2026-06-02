package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionStatusResponse(
        PlanType planType,
        String productId,
        boolean verified,
        String verificationSource,
        String transactionId,
        String originalTransactionId,
        String environment,
        Instant expiresAt,
        Instant revokedAt,
        String entitlementStatus,
        UUID appAccountToken,
        // Plan NACH dem aktuellen expiresAt, abgeleitet aus der Renewal-Info:
        // FREE = läuft aus, PLUS/PRO = verlängert sich (gleicher Plan oder Wechsel),
        // null = noch unbekannt (keine S2S-Renewal-Info). Steuert die "danach …"-Anzeige.
        PlanType nextPlanType
) {
    public SubscriptionStatusResponse(PlanType planType, String productId, boolean verified) {
        this(
                planType,
                productId,
                verified,
                verified ? "free_fallback" : "none",
                null,
                null,
                null,
                null,
                null,
                verified ? "ACTIVE" : "UNKNOWN",
                null,
                null
        );
    }

    public SubscriptionStatusResponse(
            PlanType planType,
            String productId,
            boolean verified,
            String verificationSource,
            String transactionId,
            String originalTransactionId,
            String environment
    ) {
        this(
                planType,
                productId,
                verified,
                verificationSource,
                transactionId,
                originalTransactionId,
                environment,
                null,
                null,
                verified ? "ACTIVE" : "UNKNOWN",
                null,
                null
        );
    }
}
