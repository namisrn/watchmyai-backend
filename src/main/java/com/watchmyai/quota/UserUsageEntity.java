package com.watchmyai.quota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@SuppressWarnings({"unused", "JpaDataSourceORMInspection"})
@Table(
        name = "user_usage",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_usage_user_period",
                        columnNames = {"user_id", "period_year_month"}
                )
        }
)
public class UserUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Column(name = "period_year_month", nullable = false)
    private String periodYearMonth;

    @Column(name = "period_day", nullable = false)
    private String periodDay;

    @Column(name = "used_lifetime_requests", nullable = false)
    private int usedLifetimeRequests;

    @Column(name = "used_daily_requests", nullable = false)
    private int usedDailyRequests;

    @Column(name = "used_monthly_requests", nullable = false)
    private int usedMonthlyRequests;

    @Column(name = "used_premium_requests", nullable = false)
    private int usedPremiumRequests;

    @Column(name = "estimated_monthly_cost_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedMonthlyCostEur;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserUsageEntity() {
    }

    public UserUsageEntity(String userId, PlanType planType, String periodYearMonth) {
        this(userId, planType, periodYearMonth, periodYearMonth + "-01");
    }

    public UserUsageEntity(String userId, PlanType planType, String periodYearMonth, String periodDay) {
        this.userId = userId;
        this.planType = planType;
        this.periodYearMonth = periodYearMonth;
        this.periodDay = periodDay;
        this.usedLifetimeRequests = 0;
        this.usedDailyRequests = 0;
        this.usedMonthlyRequests = 0;
        this.usedPremiumRequests = 0;
        this.estimatedMonthlyCostEur = BigDecimal.ZERO;
    }

    @PrePersist
    public void markCreated() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public String getPeriodYearMonth() {
        return periodYearMonth;
    }

    public String getPeriodDay() {
        return periodDay;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getUsedLifetimeRequests() {
        return usedLifetimeRequests;
    }

    public int getUsedDailyRequests() {
        return usedDailyRequests;
    }

    public int getUsedMonthlyRequests() {
        return usedMonthlyRequests;
    }

    public int getUsedPremiumRequests() {
        return usedPremiumRequests;
    }

    public BigDecimal getEstimatedMonthlyCostEur() {
        return estimatedMonthlyCostEur;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }

    public void incrementLifetimeRequests() {
        this.usedLifetimeRequests++;
    }

    public void incrementDailyRequests() {
        this.usedDailyRequests++;
    }

    public void incrementMonthlyRequests() {
        this.usedMonthlyRequests++;
    }

    public void incrementPremiumRequests() {
        this.usedPremiumRequests++;
    }

    public void addEstimatedMonthlyCostEur(BigDecimal costEur) {
        this.estimatedMonthlyCostEur = this.estimatedMonthlyCostEur.add(costEur);
    }

    public void reset() {
        this.usedLifetimeRequests = 0;
        this.usedDailyRequests = 0;
        this.usedMonthlyRequests = 0;
        this.usedPremiumRequests = 0;
        this.estimatedMonthlyCostEur = BigDecimal.ZERO;
    }

    public void simulateHighCost() {
        this.estimatedMonthlyCostEur = new BigDecimal("99.000000");
    }

    public void resetDailyUsage(String periodDay) {
        this.periodDay = periodDay;
        this.usedDailyRequests = 0;
    }

    /**
     * Plan-downgrade reset: zero the period counters (daily + monthly + premium) so the
     * user gets a fresh budget on the new lower plan, but preserve the lifetime counter
     * and accumulated EUR cost (historical / plan-independent fields).
     */
    public void resetForPlanDowngrade() {
        this.usedDailyRequests = 0;
        this.usedMonthlyRequests = 0;
        this.usedPremiumRequests = 0;
    }
}
