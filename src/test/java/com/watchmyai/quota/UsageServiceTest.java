package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageServiceTest {

    private static final String TEST_USER_ID = "test-user";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-04-15T10:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String CURRENT_PERIOD = "2026-04";
    private static final PlanLimits FREE_LIMITS =
            new PlanLimits(PlanType.FREE, 20, 5, 20, 0, 180, new BigDecimal("0.010000"));

    private UserUsageRepository userUsageRepository;
    private PlanConfigService planConfigService;
    private UsageService usageService;

    @BeforeEach
    void setUp() {
        userUsageRepository = mock(UserUsageRepository.class);
        planConfigService = mock(PlanConfigService.class);
        UserContextService userContextService = mock(UserContextService.class);
        UserPlanService userPlanService = mock(UserPlanService.class);
        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity(TEST_USER_ID));
        when(userPlanService.getCurrentPlan())
                .thenReturn(PlanType.FREE);

        usageService = new UsageService(
                userUsageRepository,
                userContextService,
                userPlanService,
                planConfigService,
                FIXED_CLOCK
        );
    }

    @Test
    void getCurrentUsageCreatesUsageWhenMissing() {
        UserUsageEntity createdUsage = new UserUsageEntity(
                TEST_USER_ID,
                PlanType.FREE,
                CURRENT_PERIOD
        );

        when(userUsageRepository.findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD))
                .thenReturn(Optional.empty());

        when(userUsageRepository.save(any(UserUsageEntity.class)))
                .thenReturn(createdUsage);

        UsageSnapshot snapshot = usageService.getCurrentUsage();

        assertThat(snapshot.usedLifetimeRequests()).isZero();
        assertThat(snapshot.usedDailyRequests()).isZero();
        assertThat(snapshot.usedMonthlyRequests()).isZero();
        assertThat(snapshot.usedPremiumRequests()).isZero();
        assertThat(snapshot.estimatedMonthlyCostEur()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(userUsageRepository).findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD);
        verify(userUsageRepository).save(any(UserUsageEntity.class));
    }

    @Test
    void reserveRequestReturnsTrueAndCountsLifetimeForFreePlan() {
        UserUsageEntity existingUsage = new UserUsageEntity(TEST_USER_ID, PlanType.FREE, CURRENT_PERIOD);
        when(userUsageRepository.findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD))
                .thenReturn(Optional.of(existingUsage));
        when(planConfigService.getLimits(PlanType.FREE)).thenReturn(FREE_LIMITS);
        when(userUsageRepository.reserveSlot(
                eq(TEST_USER_ID), eq(CURRENT_PERIOD), eq(PlanType.FREE),
                eq(5), eq(20), eq(20), eq(1),
                eq(new BigDecimal("0.010000")), any(Instant.class)
        )).thenReturn(1);

        boolean reserved = usageService.reserveRequest(PlanType.FREE);

        assertThat(reserved).isTrue();
        verify(userUsageRepository).reserveSlot(
                eq(TEST_USER_ID), eq(CURRENT_PERIOD), eq(PlanType.FREE),
                eq(5), eq(20), eq(20), eq(1),
                eq(new BigDecimal("0.010000")), any(Instant.class)
        );
    }

    @Test
    void reserveRequestReturnsFalseWhenQuotaExhausted() {
        UserUsageEntity existingUsage = new UserUsageEntity(TEST_USER_ID, PlanType.FREE, CURRENT_PERIOD);
        when(userUsageRepository.findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD))
                .thenReturn(Optional.of(existingUsage));
        when(planConfigService.getLimits(PlanType.FREE)).thenReturn(FREE_LIMITS);
        when(userUsageRepository.reserveSlot(
                any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any()
        )).thenReturn(0);

        boolean reserved = usageService.reserveRequest(PlanType.FREE);

        assertThat(reserved).isFalse();
    }

    @Test
    void refundRequestRevertsSlotWithLifetimeForFreePlan() {
        usageService.refundRequest(PlanType.FREE);

        verify(userUsageRepository).refundSlot(
                eq(TEST_USER_ID), eq(CURRENT_PERIOD), eq(1), any(Instant.class)
        );
    }

    @Test
    void refundRequestRevertsSlotWithoutLifetimeForPaidPlan() {
        usageService.refundRequest(PlanType.PLUS);

        verify(userUsageRepository).refundSlot(
                eq(TEST_USER_ID), eq(CURRENT_PERIOD), eq(0), any(Instant.class)
        );
    }

    @Test
    void finalizeRequestRecordsCostAndPremium() {
        usageService.finalizeRequest(PlanType.PRO, new BigDecimal("0.020000"), true);

        verify(userUsageRepository).finalizeCost(
                eq(TEST_USER_ID), eq(CURRENT_PERIOD),
                eq(new BigDecimal("0.020000")), eq(1), any(Instant.class)
        );
    }

    @Test
    void finalizeRequestRecordsCostWithoutPremium() {
        usageService.finalizeRequest(PlanType.PLUS, new BigDecimal("0.005000"), false);

        verify(userUsageRepository).finalizeCost(
                eq(TEST_USER_ID), eq(CURRENT_PERIOD),
                eq(new BigDecimal("0.005000")), eq(0), any(Instant.class)
        );
    }
}
