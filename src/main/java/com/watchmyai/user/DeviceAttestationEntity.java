package com.watchmyai.user;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Record of a verified Apple App Attest device key. One row per attested device key id.
 * The presence of a row (with its UNIQUE key_id) is what makes the guest identity
 * {@code guest:<hash(keyId)>} trustworthy and unique — a genuine Secure-Enclave-backed
 * attestation had to succeed before it was written.
 */
@Entity
@SuppressWarnings({"unused", "FieldCanBeLocal", "JpaDataSourceORMInspection"})
@Table(
        name = "device_attestation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_attestation_key_id", columnNames = "key_id")
        }
)
public class DeviceAttestationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false, length = 512)
    private String keyId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "attestation_object", nullable = false)
    private byte[] attestationObject;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceAttestationEntity() {
    }

    public DeviceAttestationEntity(String keyId, String userId, byte[] attestationObject, long signCount) {
        this.keyId = keyId;
        this.userId = userId;
        this.attestationObject = attestationObject;
        this.signCount = signCount;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getUserId() {
        return userId;
    }

    public long getSignCount() {
        return signCount;
    }

    public void setSignCount(long signCount) {
        this.signCount = signCount;
    }
}
