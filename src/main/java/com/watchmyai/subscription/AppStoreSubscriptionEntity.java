package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@SuppressWarnings({"unused", "FieldCanBeLocal", "JpaDataSourceORMInspection"})
@Table(name = "app_store_subscription")
public class AppStoreSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "original_transaction_id", nullable = false, unique = true)
    private String originalTransactionId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Column(name = "environment", nullable = false)
    private String environment;

    @Column(name = "app_account_token")
    private UUID appAccountToken;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "grace_period", nullable = false)
    private boolean gracePeriod;

    @Column(name = "billing_retry", nullable = false)
    private boolean billingRetry;

    @Column(name = "verification_source", nullable = false)
    private String verificationSource;

    @Column(name = "last_notification_type")
    private String lastNotificationType;

    @Column(name = "last_notification_subtype")
    private String lastNotificationSubtype;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppStoreSubscriptionEntity() {
    }

    public AppStoreSubscriptionEntity(String userId, String originalTransactionId) {
        this.userId = userId;
        this.originalTransactionId = originalTransactionId;
    }

    @PrePersist
    public void markCreated() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void markUpdated() {
        updatedAt = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public String getOriginalTransactionId() {
        return originalTransactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getProductId() {
        return productId;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getStatus() {
        return status;
    }

    public UUID getAppAccountToken() {
        return appAccountToken;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public String getVerificationSource() {
        return verificationSource;
    }

    public void update(SubscriptionUpdatePayload p) {
        this.transactionId = p.transactionId();
        this.productId = p.productId();
        this.planType = p.planType();
        this.environment = p.environment();
        this.appAccountToken = p.appAccountToken();
        this.status = p.status();
        this.active = p.active();
        this.expiresAt = p.expiresAt();
        this.revokedAt = p.revokedAt();
        this.revocationReason = p.revocationReason();
        this.gracePeriod = p.gracePeriod();
        this.billingRetry = p.billingRetry();
        this.verificationSource = p.verificationSource();
        this.lastNotificationType = p.lastNotificationType();
        this.lastNotificationSubtype = p.lastNotificationSubtype();
        this.lastVerifiedAt = p.lastVerifiedAt();
    }

    public void deactivate(String status, Instant lastVerifiedAt) {
        this.active = false;
        this.status = status;
        this.lastVerifiedAt = lastVerifiedAt;
    }
}
