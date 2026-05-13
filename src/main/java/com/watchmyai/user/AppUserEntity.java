package com.watchmyai.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@SuppressWarnings({"unused", "FieldCanBeLocal", "JpaDataSourceORMInspection"})
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_user_user_id", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_app_user_apple_subject", columnNames = "apple_subject"),
                @UniqueConstraint(name = "uk_app_user_app_account_token", columnNames = "app_account_token")
        }
)
public class AppUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "apple_subject", nullable = false)
    private String appleSubject;

    @Column(name = "apple_user_id")
    private String appleUserId;

    @Column(name = "email")
    private String email;

    @Column(name = "app_account_token", nullable = false)
    private UUID appAccountToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUserEntity() {
    }

    public AppUserEntity(String appleSubject, String appleUserId, String email) {
        this.userId = "apple:" + appleSubject;
        this.appleSubject = appleSubject;
        this.appleUserId = appleUserId;
        this.email = email;
        this.appAccountToken = UUID.randomUUID();
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

    public String getAppleSubject() {
        return appleSubject;
    }

    public UUID getAppAccountToken() {
        return appAccountToken;
    }

    public void updateAppleProfile(String appleUserId, String email) {
        if (appleUserId != null && !appleUserId.isBlank()) {
            this.appleUserId = appleUserId;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
    }
}
