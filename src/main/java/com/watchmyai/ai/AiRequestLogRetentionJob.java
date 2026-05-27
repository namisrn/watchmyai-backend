package com.watchmyai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * GDPR Art. 5 (1) (e) — Speicherbegrenzung. Automatischer Purge der
 * {@code ai_request_log.answer}-Spalte nach Ablauf der Aufbewahrungsfrist,
 * die in {@code legal/PRIVACY_POLICY.md} § 10 und {@code legal/ROPA.md} § 6
 * mit Nutzern kommuniziert ist.
 *
 * <p>Was gelöscht wird: der generierte Antworttext (potenziell personenbezogen,
 * weil der Wortlaut den ursprünglichen Prompt erahnen lässt). Was bleibt:
 * aggregierte Metadaten (Plan, Modell, Token-Counts, geschätzte Kosten) — diese
 * sind nach Datenminimierungsprinzip nicht personenbezogen und werden weitere
 * 24 Monate für die monatliche Kosten-Reconciliation gegen die OpenAI-Rechnung
 * benötigt.
 *
 * <p>Default-Schedule: täglich um 03:30 Europe/Berlin (Cron). Zu dieser Zeit ist
 * die Last minimal und die Maintenance-Window-Überlappung mit Hetzner-Snapshots
 * unwahrscheinlich. Konfigurierbar über die Property
 * {@code watchmyai.retention.ai-request-log.cron} falls eine andere Zeit gewünscht ist.
 *
 * <p>Default-Retention: 30 Tage. Über {@code watchmyai.retention.ai-request-log.days}
 * konfigurierbar. Verkürzung der Frist möglich (z. B. auf 7 Tage), Verlängerung
 * würde eine Anpassung der Privacy Policy + ROPA + DPIA voraussetzen — der Code
 * lässt sie zu, die Compliance verbietet sie ohne Doku-Update.
 *
 * <p>Idempotent: mehrfache Ausführung am gleichen Tag verändert nichts mehr
 * (gleiche Rows haben dann {@code answer IS NULL}), die Update-Query no-ops.
 *
 * <p>Singleton-Annahme: derzeit Single-Pod-Deployment. Bei späterem Horizontal
 * Scaling muss ein Distributed Lock vorgeschaltet werden (Redis-basiert,
 * Redisson o.ä.) damit nicht zwei Pods parallel purgen — funktional unkritisch
 * (idempotent), aber unnötige DB-Last.
 */
@Component
public class AiRequestLogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AiRequestLogRetentionJob.class);

    private final AiRequestLogRepository repository;
    private final Clock clock;
    private final int retentionDays;

    public AiRequestLogRetentionJob(
            AiRequestLogRepository repository,
            Clock clock,
            @Value("${watchmyai.retention.ai-request-log.days:30}") int retentionDays
    ) {
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
        if (retentionDays < 1) {
            throw new IllegalStateException(
                    "watchmyai.retention.ai-request-log.days must be >= 1, was " + retentionDays
            );
        }
    }

    /**
     * Täglicher Purge. Die Cron-Expression läuft in der Server-Zeitzone (default UTC),
     * was bei Hetzner-DE faktisch UTC+1/+2 → 02:30/03:30 lokal entspricht. Über die
     * {@code zone}-Eigenschaft auf Europe/Berlin fixiert, damit Sommer-/Winterzeit
     * keine Verschiebung erzeugen.
     */
    @Scheduled(cron = "${watchmyai.retention.ai-request-log.cron:0 30 3 * * *}", zone = "Europe/Berlin")
    @Transactional
    public void purgeExpiredAnswers() {
        Instant threshold = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
        int updated = repository.purgeAnswersOlderThan(threshold);
        if (updated > 0) {
            log.info(
                    "GDPR retention purge complete: cleared answer column on {} rows older than {} ({} days)",
                    updated, threshold, retentionDays
            );
        } else {
            log.debug(
                    "GDPR retention purge no-op: no rows older than {} ({} days) with non-null answer",
                    threshold, retentionDays
            );
        }
    }
}
