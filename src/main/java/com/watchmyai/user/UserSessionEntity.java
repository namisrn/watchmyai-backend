package com.watchmyai.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@SuppressWarnings({"unused", "FieldCanBeLocal", "JpaDataSourceORMInspection"})
@Table(
        name = "user_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_session_token_hash", columnNames = "token_hash")
        }
)
public class UserSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSessionEntity() {
    }

    public UserSessionEntity(String tokenHash, String userId, String source, String deviceName, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.source = source;
        this.deviceName = deviceName;
        this.expiresAt = expiresAt;
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

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        revokedAt = now;
    }

    public void extend(Instant newExpiresAt) {
        expiresAt = newExpiresAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
