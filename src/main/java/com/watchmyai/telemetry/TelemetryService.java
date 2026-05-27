package com.watchmyai.telemetry;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Schreibt Product-Telemetrie-Events in die {@code telemetry_event}-Tabelle.
 *
 * <p>Das Tor zur DB. Alle Events — egal ob server-instrumentiert oder von
 * iOS/Watch durchgereicht — landen über {@link #record(String, String, String, String, String, String, Map)}.
 * Hier passieren die drei nicht-verhandelbaren Sanitization-Schritte:
 *
 * <ol>
 *   <li><b>User-ID-Hashing</b>: nie die rohe Apple-Subject-ID in der Telemetrie-Tabelle.
 *       SHA-256, dann auf 128 bit Hex gekürzt — reicht für Kohorten-Joins, ist aber ohne
 *       separates Mapping nicht re-identifizierbar.</li>
 *   <li><b>Event-Name-Validierung</b>: nur Snake-Case-ASCII, max 64 chars. Verhindert
 *       SQL-Komische-Zeichen und Datenbank-Constraint-Verletzungen.</li>
 *   <li><b>Property-Key-Blocklist</b>: bricht hart ab, wenn jemand versehentlich
 *       "prompt", "answer", "email", "input", "content", "name", "phone", "address",
 *       "passwort"… einliefert. Defense-in-depth gegen Programmierfehler oder
 *       Forwarding-Bugs aus dem iOS-Client.</li>
 * </ol>
 *
 * <p>Die Sanitization wirft bei Verstößen — wir wollen lieber einen WARN-Log im
 * Server als einen unbemerkten PII-Leak in die Telemetrie. Aufrufer sollen den
 * Aufruf in try/catch wrappen, damit Telemetrie nie eine Business-Action killt.
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    /**
     * Properties die nie in der Telemetrie landen dürfen. Ergänzbar ohne Migration —
     * die Erweiterung gilt sofort für alle eingehenden Events. Die Liste ist case-
     * insensitiv (Vergleich gegen lowercased Key).
     */
    private static final Set<String> FORBIDDEN_PROPERTY_KEYS = Set.of(
            "prompt", "answer", "input", "content", "message", "text",
            "email", "name", "phone", "address", "passwort", "password",
            "token", "secret", "apikey", "api_key",
            "userid", "user_id", "appleuserid", "apple_user_id",
            "ip", "ipaddress", "ip_address"
    );

    /** Event-Name nur Snake-Case-ASCII, 1-64 Zeichen. */
    private static final java.util.regex.Pattern EVENT_NAME_PATTERN =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private final TelemetryEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TelemetryService(
            TelemetryEventRepository repository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Schreibt ein einzelnes Event. Bei Verstoß gegen die Sanitization-Regeln wirft
     * die Methode {@link TelemetryRejectedException} statt schweigend zu schlucken —
     * so wird ein Programmierfehler im Aufrufer schnell sichtbar.
     *
     * @param eventName     snake_case, 1-64 chars
     * @param userId        rohe interne User-ID (z. B. {@code "apple:..."}); null für anonyme Events
     * @param platform      {@code "ios"}, {@code "watch"}, {@code "backend"}; null erlaubt
     * @param plan          {@code "free"}, {@code "plus"}, {@code "pro"}; null erlaubt
     * @param locale        z. B. {@code "de-DE"}; null erlaubt
     * @param appVersion    z. B. {@code "1.2.0+48"}; null erlaubt
     * @param properties    zusätzliche Metadaten; Keys werden gegen die Blocklist geprüft
     */
    @Transactional
    public void record(
            String eventName,
            String userId,
            String platform,
            String plan,
            String locale,
            String appVersion,
            Map<String, Object> properties
    ) {
        record(eventName, userId, platform, plan, locale, appVersion, properties, Instant.now(clock));
    }

    /** Variante mit explizitem Zeitstempel — für iOS-Events die bereits Latenz hatten. */
    @Transactional
    public void record(
            String eventName,
            String userId,
            String platform,
            String plan,
            String locale,
            String appVersion,
            Map<String, Object> properties,
            Instant occurredAt
    ) {
        String validatedName = validateEventName(eventName);
        String validatedPlatform = validateEnum("platform", platform, Set.of("ios", "watch", "backend"));
        String validatedPlan = validateEnum("plan", plan, Set.of("free", "plus", "pro"));
        String userIdHash = hashUserId(userId);
        Map<String, Object> sanitizedProps = sanitizeProperties(properties);
        String propsJson = serializeProps(sanitizedProps);

        TelemetryEventEntity entity = new TelemetryEventEntity(
                validatedName,
                occurredAt,
                userIdHash,
                validatedPlatform,
                validatedPlan,
                truncate(locale, 16),
                truncate(appVersion, 32),
                propsJson
        );
        repository.save(entity);
    }

    /**
     * Hash für Account-Löschung. Externe Aufrufer (z. B. {@code AccountDeletionService})
     * brauchen denselben Hash um die Events des Users zu finden.
     */
    public String hashUserIdForDeletion(String userId) {
        return hashUserId(userId);
    }

    // --- internals ---

    private String validateEventName(String name) {
        if (name == null || !EVENT_NAME_PATTERN.matcher(name).matches()) {
            throw new TelemetryRejectedException(
                    "Telemetry event name must match " + EVENT_NAME_PATTERN.pattern() + ", got: " + name
            );
        }
        return name;
    }

    private String validateEnum(String field, String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(lower)) {
            throw new TelemetryRejectedException(
                    "Telemetry " + field + " must be one of " + allowed + ", got: " + value
            );
        }
        return lower;
    }

    private String hashUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userId.getBytes(StandardCharsets.UTF_8));
            // Truncate to 16 bytes (128 bit) = 32 hex chars. Reicht für
            // Eindeutigkeit auf der Größenordnung unserer User-Basis, halbiert
            // den Storage gegenüber vollem SHA-256.
            byte[] truncated = new byte[16];
            System.arraycopy(hash, 0, truncated, 0, 16);
            return HexFormat.of().formatHex(truncated);
        } catch (NoSuchAlgorithmException e) {
            // Sollte nie passieren — SHA-256 ist in jeder JVM Pflicht.
            throw new TelemetryRejectedException("SHA-256 not available", e);
        }
    }

    private Map<String, Object> sanitizeProperties(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(input.size());
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
            if (FORBIDDEN_PROPERTY_KEYS.contains(normalized)) {
                throw new TelemetryRejectedException(
                        "Telemetry property key '" + key + "' is on the PII blocklist."
                );
            }
            Object value = entry.getValue();
            // Long strings sind ein PII-Risiko. Cap auf 256 chars pro String-Value.
            if (value instanceof String s && s.length() > 256) {
                value = s.substring(0, 256) + "…";
            }
            sanitized.put(normalized, value);
        }
        return sanitized;
    }

    private String serializeProps(Map<String, Object> props) {
        if (props.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(props);
        } catch (RuntimeException e) {
            log.warn("Failed to serialize telemetry properties: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
