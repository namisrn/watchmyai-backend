package com.watchmyai.quota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface UserUsageRepository extends JpaRepository<UserUsageEntity, Long> {

    Optional<UserUsageEntity> findByUserIdAndPeriodYearMonth(
            String userId,
            String periodYearMonth
    );

    /**
     * Atomically reserves one request slot. The conditional UPDATE only increments the
     * counters when every limit is still satisfied, so concurrent callers cannot both pass
     * a stale check-then-act window. Returns the number of affected rows: 1 when the slot
     * was reserved, 0 when a limit is exhausted.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserUsageEntity u SET
                u.planType = :planType,
                u.usedDailyRequests = u.usedDailyRequests + 1,
                u.usedMonthlyRequests = u.usedMonthlyRequests + 1,
                u.usedLifetimeRequests = u.usedLifetimeRequests + :lifetimeIncrement,
                u.updatedAt = :now
            WHERE u.userId = :userId
                AND u.periodYearMonth = :periodYearMonth
                AND u.usedDailyRequests < :dailyLimit
                AND u.usedMonthlyRequests < :monthlyLimit
                AND (:lifetimeLimit <= 0 OR u.usedLifetimeRequests < :lifetimeLimit)
                AND u.estimatedMonthlyCostEur < :costCap
            """)
    int reserveSlot(
            @Param("userId") String userId,
            @Param("periodYearMonth") String periodYearMonth,
            @Param("planType") PlanType planType,
            @Param("dailyLimit") int dailyLimit,
            @Param("monthlyLimit") int monthlyLimit,
            @Param("lifetimeLimit") int lifetimeLimit,
            @Param("lifetimeIncrement") int lifetimeIncrement,
            @Param("costCap") BigDecimal costCap,
            @Param("now") Instant now
    );

    /**
     * Reverts a previously reserved slot when the downstream provider call failed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserUsageEntity u SET
                u.usedDailyRequests = CASE WHEN u.usedDailyRequests > 0 THEN u.usedDailyRequests - 1 ELSE 0 END,
                u.usedMonthlyRequests = CASE WHEN u.usedMonthlyRequests > 0 THEN u.usedMonthlyRequests - 1 ELSE 0 END,
                u.usedLifetimeRequests = CASE WHEN u.usedLifetimeRequests >= :lifetimeIncrement THEN u.usedLifetimeRequests - :lifetimeIncrement ELSE 0 END,
                u.updatedAt = :now
            WHERE u.userId = :userId
                AND u.periodYearMonth = :periodYearMonth
            """)
    int refundSlot(
            @Param("userId") String userId,
            @Param("periodYearMonth") String periodYearMonth,
            @Param("lifetimeIncrement") int lifetimeIncrement,
            @Param("now") Instant now
    );

    /**
     * Records the actual cost after a successful provider call. Premium-slot accounting is
     * handled separately by {@link #reservePremiumSlot} (decided BEFORE the provider call,
     * so the gate is atomic instead of a snapshot-then-act race).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserUsageEntity u SET
                u.estimatedMonthlyCostEur = u.estimatedMonthlyCostEur + :costEur,
                u.updatedAt = :now
            WHERE u.userId = :userId
                AND u.periodYearMonth = :periodYearMonth
            """)
    int finalizeCost(
            @Param("userId") String userId,
            @Param("periodYearMonth") String periodYearMonth,
            @Param("costEur") BigDecimal costEur,
            @Param("now") Instant now
    );

    /**
     * Atomically reserves one premium slot. Increments {@code usedPremiumRequests} only when
     * the limit is still satisfied — closes the race in the AI worker where N concurrent Pro
     * requests previously all read a stale snapshot and all decided "premium available".
     * Returns 1 when reserved, 0 when the premium limit is exhausted or {@code premiumLimit <= 0}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserUsageEntity u SET
                u.usedPremiumRequests = u.usedPremiumRequests + 1,
                u.updatedAt = :now
            WHERE u.userId = :userId
                AND u.periodYearMonth = :periodYearMonth
                AND :premiumLimit > 0
                AND u.usedPremiumRequests < :premiumLimit
            """)
    int reservePremiumSlot(
            @Param("userId") String userId,
            @Param("periodYearMonth") String periodYearMonth,
            @Param("premiumLimit") int premiumLimit,
            @Param("now") Instant now
    );

    /**
     * Refunds a previously reserved premium slot when the provider call subsequently failed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserUsageEntity u SET
                u.usedPremiumRequests = CASE WHEN u.usedPremiumRequests > 0 THEN u.usedPremiumRequests - 1 ELSE 0 END,
                u.updatedAt = :now
            WHERE u.userId = :userId
                AND u.periodYearMonth = :periodYearMonth
            """)
    int refundPremiumSlot(
            @Param("userId") String userId,
            @Param("periodYearMonth") String periodYearMonth,
            @Param("now") Instant now
    );
}
