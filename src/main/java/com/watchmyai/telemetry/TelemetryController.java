package com.watchmyai.telemetry;

import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Empfängt Batch-Telemetrie vom iOS/Watch-Client.
 *
 * <p>Apple-Sign-In-Auth ist nicht erzwingbar, weil Telemetrie auch vor dem ersten
 * Sign-In sinnvoll ist (Activation-Funnel, Crash-Onboarding). Wir nehmen die
 * userId aus {@link UserContextService}, wenn die Session vorhanden ist, sonst
 * werden Events anonym gespeichert.
 *
 * <p>Rate-Limit: Eintrag in {@code ApiRateLimitFilter} mit 60 Batches/Min/IP
 * (s. dort) — das deckt einen normalen Client (Batch alle 30s) mit Reserve ab,
 * stoppt aber einen klassischen Spammer.
 */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);
    /** Hartes Cap pro Batch — schützt vor DoS via überdimensioniertem Request. */
    private static final int MAX_EVENTS_PER_BATCH = 50;

    private final TelemetryService telemetryService;
    private final UserContextService userContextService;

    public TelemetryController(
            TelemetryService telemetryService,
            UserContextService userContextService
    ) {
        this.telemetryService = telemetryService;
        this.userContextService = userContextService;
    }

    @PostMapping("/events")
    public ResponseEntity<TelemetryBatchResponse> ingest(@Valid @RequestBody TelemetryBatchRequest request) {
        List<TelemetryEventDto> events = request.events();
        if (events.size() > MAX_EVENTS_PER_BATCH) {
            return ResponseEntity.badRequest().body(
                    new TelemetryBatchResponse(0, events.size(),
                            "Batch larger than " + MAX_EVENTS_PER_BATCH + " events.")
            );
        }

        // Session ist optional — wenn keine vorhanden, bleibt userId null und die
        // Events sind anonym (sinnvoll für Pre-Sign-In-Events wie app_first_launch).
        String userId = null;
        try {
            UserIdentity identity = userContextService.getCurrentUser();
            if (identity != null) {
                userId = identity.userId();
            }
        } catch (RuntimeException ignored) {
            // Kein Session-Header → anonym, kein Fehler.
        }

        int accepted = 0;
        int rejected = 0;
        for (TelemetryEventDto event : events) {
            try {
                telemetryService.record(
                        event.eventName(),
                        userId,
                        event.platform(),
                        event.plan(),
                        event.locale(),
                        event.appVersion(),
                        event.properties(),
                        event.occurredAt() != null ? event.occurredAt() : Instant.now()
                );
                accepted++;
            } catch (TelemetryRejectedException e) {
                // Einzelnes Event wird verworfen, der Batch läuft weiter. Wir loggen den
                // Grund, damit der iOS-Client-Bug schnell sichtbar wird, schicken aber
                // KEINEN Detail-Error zurück — sonst könnte ein Client den Hash auf die
                // Blocklist-Logik mappen.
                rejected++;
                log.warn("Telemetry event rejected: {} (event={})", e.getMessage(), event.eventName());
            } catch (RuntimeException e) {
                // Persistenz-Fehler etc. — Batch wird abgebrochen mit 500, Client wiederholt.
                log.error("Telemetry batch persist failed at event {} of {}",
                        accepted + rejected + 1, events.size(), e);
                return ResponseEntity.internalServerError().body(
                        new TelemetryBatchResponse(accepted, events.size() - accepted, "Persistence error")
                );
            }
        }

        return ResponseEntity.ok(new TelemetryBatchResponse(accepted, rejected, null));
    }

    /**
     * Single-Event-Wrapper für iOS/Watch-Clients. {@code properties} ist optional
     * und enthält Event-spezifische Metadaten. Verbotene Property-Keys (PII)
     * werden server-seitig abgelehnt, der Client filtert vorab.
     */
    public record TelemetryEventDto(
            @NotNull @Size(max = 64) String eventName,
            @Size(max = 16) String platform,
            @Size(max = 16) String plan,
            @Size(max = 16) String locale,
            @Size(max = 32) String appVersion,
            Map<String, Object> properties,
            Instant occurredAt
    ) {}

    public record TelemetryBatchRequest(
            @NotEmpty @Size(max = MAX_EVENTS_PER_BATCH) List<@Valid TelemetryEventDto> events
    ) {}

    public record TelemetryBatchResponse(int accepted, int rejected, String message) {}
}
