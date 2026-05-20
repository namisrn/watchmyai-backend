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
        assertThat(quota.throttleState()).isEqualTo("normal");
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
        assertThat(quota.throttleState()).isEqualTo("capped");
    }
}
