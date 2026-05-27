package com.watchmyai.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLogEntity, Long> {

    Optional<AiRequestLogEntity> findByUserIdAndClientRequestId(
            String userId,
            String clientRequestId
    );

    void deleteByUserId(String userId);

    /**
     * Retention purge for {@code ai_request_log.answer}. Sets the column to NULL
     * for every row whose {@code created_at} is older than {@code threshold} and
     * still has an answer value. Aggregated cost/token metadata is intentionally
     * preserved (no PII) for monthly cost reconciliation per
     * {@code legal/ROPA.md} § 6 and {@code legal/DPIA.md} § 1.4.
     *
     * <p>Note: {@code updatedAt} is intentionally not touched in this bulk
     * UPDATE — JPQL bulk operations skip JPA lifecycle callbacks (so the
     * {@code @PreUpdate} on the entity wouldn't fire anyway), and "the row's
     * original write time" is the more honest semantic for a redaction:
     * we're clearing user-facing content while keeping the audit trail of
     * when the request first happened. The fact that redaction occurred is
     * recorded in the {@code AiRequestLogRetentionJob}'s log line, not in
     * the row itself.
     *
     * @return number of rows updated, for the scheduled job's log line.
     */
    @Modifying
    @Query("""
            UPDATE AiRequestLogEntity log
               SET log.answer = NULL
             WHERE log.answer IS NOT NULL
               AND log.createdAt < :threshold
            """)
    int purgeAnswersOlderThan(@Param("threshold") Instant threshold);
}
