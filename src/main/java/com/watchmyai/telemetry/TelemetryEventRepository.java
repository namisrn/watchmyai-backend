package com.watchmyai.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TelemetryEventRepository extends JpaRepository<TelemetryEventEntity, Long> {

    /**
     * Retention purge: löscht Events älter als {@code threshold}. Wird vom künftigen
     * {@code TelemetryRetentionJob} aufgerufen (analog {@code AiRequestLogRetentionJob}).
     * Aktuelle Frist: 12 Monate (siehe {@code legal/ROPA.md} § 8). Die einzelnen
     * Events werden danach hart gelöscht — nicht anonymisiert — weil sie bereits
     * vor dem INSERT PII-frei sanitisiert wurden.
     *
     * @return Anzahl gelöschter Zeilen.
     */
    @Modifying
    @Query("DELETE FROM TelemetryEventEntity e WHERE e.occurredAt < :threshold")
    int purgeOlderThan(@Param("threshold") Instant threshold);

    /**
     * Löscht alle Events eines Users. Wird von {@code AccountDeletionService} aufgerufen
     * wenn der User seine Löschung anfordert (Art. 17 DSGVO). Der user_id_hash ist
     * gehashed, daher muss der Aufrufer den Hash bilden — analog zur Sanitization
     * in {@code TelemetryService}.
     *
     * @return Anzahl gelöschter Zeilen.
     */
    @Modifying
    @Query("DELETE FROM TelemetryEventEntity e WHERE e.userIdHash = :userIdHash")
    int deleteByUserIdHash(@Param("userIdHash") String userIdHash);
}
