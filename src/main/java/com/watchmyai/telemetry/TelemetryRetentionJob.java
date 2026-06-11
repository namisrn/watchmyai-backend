package com.watchmyai.telemetry;

import com.watchmyai.common.DistributedLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * GDPR Art. 5 (1) (e) — Speicherbegrenzung für Produkt-Telemetrie. Hartes Löschen
 * aller {@code telemetry_event}-Zeilen nach Ablauf der Aufbewahrungsfrist, die in
 * {@code legal/ROPA.md} § 8, {@code legal/PRIVACY_POLICY.md} § 10 und
 * {@code docs/TELEMETRY_TAXONOMY.md} § 6 kommuniziert ist.
 *
 * <p>Im Gegensatz zum {@link com.watchmyai.ai.AiRequestLogRetentionJob} wird hier
 * komplett gelöscht statt eine Spalte zu nullen: die Events sind bereits vor dem
 * INSERT durch {@link TelemetryService} PII-frei sanitisiert (User-ID gehasht,
 * Blocklist erzwungen, String-Cap) — es bleibt also nichts Schützenswertes übrig,
 * das eine Teil-Redaktion rechtfertigen würde. Aggregierte Auswertungen werden
 * laufend per SQL (siehe {@code docs/TELEMETRY_DASHBOARDS.md}) extrahiert und sind
 * als anonyme Statistik nicht mehr personenbezogen.
 *
 * <p>Default-Schedule: täglich um 03:45 Europe/Berlin — bewusst 15 Minuten nach
 * dem AI-Request-Log-Purge (03:30) gestaffelt, damit nicht zwei Bulk-Deletes
 * zeitgleich auf dieselbe DB drücken. Konfigurierbar über
 * {@code watchmyai.retention.telemetry.cron}.
 *
 * <p>Default-Retention: 365 Tage. Über {@code watchmyai.retention.telemetry.days}
 * konfigurierbar. Verlängerung der Frist würde — wie beim AI-Request-Log — ein
 * Update von Privacy Policy + ROPA voraussetzen; der Code lässt sie zu, die
 * Compliance verbietet sie ohne Doku-Update.
 *
 * <p>Idempotent: mehrfache Ausführung am gleichen Tag löscht beim zweiten Lauf
 * nichts mehr (die abgelaufenen Zeilen sind weg), die Query no-opt dann.
 *
 * <p>Singleton-Annahme wie beim AI-Request-Log-Job: bei späterem Horizontal
 * Scaling muss ein Distributed Lock vorgeschaltet werden.
 */
@Component
public class TelemetryRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(TelemetryRetentionJob.class);

    private final TelemetryEventRepository repository;
    private final Clock clock;
    private final int retentionDays;
    private final DistributedLockService distributedLockService;

    public TelemetryRetentionJob(
            TelemetryEventRepository repository,
            Clock clock,
            @Value("${watchmyai.retention.telemetry.days:365}") int retentionDays,
            DistributedLockService distributedLockService
    ) {
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.distributedLockService = distributedLockService;
        if (retentionDays < 1) {
            throw new IllegalStateException(
                    "watchmyai.retention.telemetry.days must be >= 1, was " + retentionDays
            );
        }
    }

    /**
     * Täglicher Purge. {@code zone=Europe/Berlin} fixiert die Ausführung gegen
     * Sommer-/Winterzeit-Verschiebung (analog AI-Request-Log-Job).
     */
    @Scheduled(cron = "${watchmyai.retention.telemetry.cron:0 45 3 * * *}", zone = "Europe/Berlin")
    @Transactional
    public void purgeExpiredEvents() {
        // Distributed lock: under horizontal scaling only one pod runs the purge per window; the
        // others skip it. Without Redis (single instance / dev) the task runs directly.
        distributedLockService.runExclusively("retention:telemetry", Duration.ofMinutes(10), () -> {
            Instant threshold = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
            int deleted = repository.purgeOlderThan(threshold);
            if (deleted > 0) {
                log.info(
                        "Telemetry retention purge complete: deleted {} events older than {} ({} days)",
                        deleted, threshold, retentionDays
                );
            } else {
                log.debug(
                        "Telemetry retention purge no-op: no events older than {} ({} days)",
                        threshold, retentionDays
                );
            }
        });
    }
}
