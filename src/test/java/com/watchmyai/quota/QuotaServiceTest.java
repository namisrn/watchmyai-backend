package com.watchmyai.quota;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuotaServiceTest {

    private final PlanConfigService planConfigService = new PlanConfigService();
    private final UsageService usageService = mock(UsageService.class);
    private final QuotaService quotaService = new QuotaService(planConfigService, usageService);

    @Test
    void freePlanUsesDailyAndMonthlyLimitsWithoutLifetimeCap() {
        when(usageService.getCurrentUsage())
                .thenReturn(new UsageSnapshot(
                        42,
                        1,
                        7,
                        0,
                        new BigDecimal("0.010000")
                ));

        QuotaCheckResult quota = quotaService.checkQuota(PlanType.FREE);

        assertThat(quota.requestAllowed()).isTrue();
        assertThat(quota.dailyRemainingRequests()).isEqualTo(4);
        assertThat(quota.monthlyRemainingRequests()).isEqualTo(13);
        assertThat(quota.remainingRequests()).isEqualTo(4);
        assertThat(quota.throttleState()).isEqualTo(QuotaState.NORMAL);
    }

    @Test
    void freePlanCapsOnlyWhenDailyOrMonthlyLimitIsActuallyExhausted() {
        when(usageService.getCurrentUsage())
                .thenReturn(new UsageSnapshot(
                        42,
                        5,
                        7,
                        0,
                        new BigDecimal("0.010000")
                ));

        QuotaCheckResult quota = quotaService.checkQuota(PlanType.FREE);

        assertThat(quota.requestAllowed()).isFalse();
        assertThat(quota.dailyRemainingRequests()).isZero();
        assertThat(quota.monthlyRemainingRequests()).isEqualTo(13);
        assertThat(quota.remainingRequests()).isZero();
        assertThat(quota.throttleState()).isEqualTo(QuotaState.CAPPED);
    }

    @Test
    void plusPlanEntersCarefulStateAt70PercentMonthlyUsage() {
        // PLUS: 60 daily / 500 monthly → 70% of 500 = 350
        when(usageService.getCurrentUsage())
                .thenReturn(new UsageSnapshot(
                        0,
                        10,
                        350,
                        0,
                        new BigDecimal("0.50000")
                ));

        QuotaCheckResult quota = quotaService.checkQuota(PlanType.PLUS);

        assertThat(quota.requestAllowed()).isTrue();
        assertThat(quota.throttleState()).isEqualTo(QuotaState.CAREFUL);
    }

    @Test
    void plusPlanEntersRestrictedStateAt90PercentMonthlyUsage() {
        // PLUS: 60 daily / 500 monthly → 90% of 500 = 450
        when(usageService.getCurrentUsage())
                .thenReturn(new UsageSnapshot(
                        0,
                        10,
                        450,
                        0,
                        new BigDecimal("0.50000")
                ));

        QuotaCheckResult quota = quotaService.checkQuota(PlanType.PLUS);

        assertThat(quota.requestAllowed()).isTrue();
        assertThat(quota.throttleState()).isEqualTo(QuotaState.RESTRICTED);
    }

    @Test
    void plusPlanCapsWhenMonthlyCostCapIsExceeded() {
        when(usageService.getCurrentUsage())
                .thenReturn(new UsageSnapshot(
                        0,
                        0,
                        0,
                        0,
                        new BigDecimal("999.000000")
                ));

        QuotaCheckResult quota = quotaService.checkQuota(PlanType.PLUS);

        assertThat(quota.requestAllowed()).isFalse();
        assertThat(quota.throttleState()).isEqualTo(QuotaState.CAPPED);
    }
}
