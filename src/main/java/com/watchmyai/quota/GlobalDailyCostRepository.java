package com.watchmyai.quota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface GlobalDailyCostRepository extends JpaRepository<GlobalDailyCostEntity, String> {

    /** Current accumulated spend for the day, or empty when no request has landed yet. */
    @Query(value = "SELECT total_cost_eur FROM global_daily_cost WHERE period_day = :day", nativeQuery = true)
    Optional<BigDecimal> findTotalForDay(@Param("day") String day);

    /**
     * Atomic upsert-increment: creates the day's row or adds to it in a single statement.
     * Postgres {@code ON CONFLICT} makes concurrent finalizers safe without a row lock from
     * the application side.
     */
    @Modifying
    @Query(value = """
            INSERT INTO global_daily_cost (period_day, total_cost_eur, updated_at)
            VALUES (:day, :cost, :now)
            ON CONFLICT (period_day)
            DO UPDATE SET total_cost_eur = global_daily_cost.total_cost_eur + EXCLUDED.total_cost_eur,
                          updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void incrementForDay(@Param("day") String day, @Param("cost") BigDecimal cost, @Param("now") Instant now);
}
