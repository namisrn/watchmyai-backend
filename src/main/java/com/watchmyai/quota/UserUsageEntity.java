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

import java.time.Instant;

@Entity
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

    @Column(name = "used_lifetime_requests", nullable = false)
    private int usedLifetimeRequests;

    @Column(name = "used_monthly_requests", nullable = false)
    private int usedMonthlyRequests;

    @Column(name = "used_premium_requests", nullable = false)
    private int usedPremiumRequests;

    @Column(name = "estimated_monthly_cost_eur", nullable = false)
    private double estimatedMonthlyCostEur;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserUsageEntity() {
    }

    public UserUsageEntity(String userId, PlanType planType, String periodYearMonth) {
        this.userId = userId;
        this.planType = planType;
        this.periodYearMonth = periodYearMonth;
        this.usedLifetimeRequests = 5;
        this.usedMonthlyRequests = 0;
        this.usedPremiumRequests = 0;
        this.estimatedMonthlyCostEur = 0.002;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getUsedLifetimeRequests() {
        return usedLifetimeRequests;
    }

    public int getUsedMonthlyRequests() {
        return usedMonthlyRequests;
    }

    public int getUsedPremiumRequests() {
        return usedPremiumRequests;
    }

    public double getEstimatedMonthlyCostEur() {
        return estimatedMonthlyCostEur;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }

    public void incrementLifetimeRequests() {
        this.usedLifetimeRequests++;
    }

    public void incrementMonthlyRequests() {
        this.usedMonthlyRequests++;
    }

    public void incrementPremiumRequests() {
        this.usedPremiumRequests++;
    }

    public void addEstimatedMonthlyCostEur(double costEur) {
        this.estimatedMonthlyCostEur += costEur;
    }

    public void reset() {
        this.usedLifetimeRequests = 5;
        this.usedMonthlyRequests = 0;
        this.usedPremiumRequests = 0;
        this.estimatedMonthlyCostEur = 0.002;
    }

    public void simulateHighCost() {
        this.estimatedMonthlyCostEur = 99.0;
    }
}