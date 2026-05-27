package com.watchmyai.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Single product-telemetry event. Datasparsam, PII-frei. Siehe
 * {@code legal/ROPA.md} § 8 für die Verarbeitungsbeschreibung und
 * {@code docs/TELEMETRY_TAXONOMY.md} für die Liste der gültigen Event-Namen
 * und Properties.
 */
@Entity
@Table(name = "telemetry_event")
@SuppressWarnings({"unused", "JpaDataSourceORMInspection"})
public class TelemetryEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name", nullable = false, length = 64)
    private String eventName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id_hash", length = 32)
    private String userIdHash;

    @Column(name = "platform", length = 16)
    private String platform;

    @Column(name = "plan", length = 16)
    private String plan;

    @Column(name = "locale", length = 16)
    private String locale;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "properties_json", columnDefinition = "TEXT")
    private String propertiesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TelemetryEventEntity() {
    }

    public TelemetryEventEntity(
            String eventName,
            Instant occurredAt,
            String userIdHash,
            String platform,
            String plan,
            String locale,
            String appVersion,
            String propertiesJson
    ) {
        this.eventName = eventName;
        this.occurredAt = occurredAt;
        this.userIdHash = userIdHash;
        this.platform = platform;
        this.plan = plan;
        this.locale = locale;
        this.appVersion = appVersion;
        this.propertiesJson = propertiesJson;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventName() {
        return eventName;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getUserIdHash() {
        return userIdHash;
    }

    public String getPlatform() {
        return platform;
    }

    public String getPlan() {
        return plan;
    }

    public String getLocale() {
        return locale;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getPropertiesJson() {
        return propertiesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
