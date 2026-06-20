package com.watchmyai.quota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per calendar day holding the aggregate AI provider spend across all users.
 * Written via the atomic upsert in {@link GlobalDailyCostRepository#incrementForDay}
 * and read by {@link GlobalCostGuardService}. Intentionally minimal — no per-user data,
 * so it is not personal data and is exempt from the {@code ai_request_log} retention purge.
 */
@Entity
@Table(name = "global_daily_cost")
@SuppressWarnings({"unused", "JpaDataSourceORMInspection"})
public class GlobalDailyCostEntity {

    @Id
    @Column(name = "period_day", length = 10, nullable = false)
    private String periodDay;

    @Column(name = "total_cost_eur", nullable = false, precision = 14, scale = 6)
    private BigDecimal totalCostEur;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GlobalDailyCostEntity() {
    }

    public String getPeriodDay() {
        return periodDay;
    }

    public BigDecimal getTotalCostEur() {
        return totalCostEur;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
