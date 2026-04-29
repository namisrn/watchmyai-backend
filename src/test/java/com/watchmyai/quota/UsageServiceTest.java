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

    private UserUsageRepository userUsageRepository;
    private UserPlanService userPlanService;
    private UsageService usageService;

    @BeforeEach
    void setUp() {
        userUsageRepository = mock(UserUsageRepository.class);
        UserContextService userContextService = mock(UserContextService.class);
        userPlanService = mock(UserPlanService.class);
        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity(TEST_USER_ID));
        when(userPlanService.getCurrentPlan())
                .thenReturn(PlanType.FREE);

        usageService = new UsageService(userUsageRepository, userContextService, userPlanService, FIXED_CLOCK);
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

        assertThat(snapshot.usedLifetimeRequests()).isEqualTo(5);
        assertThat(snapshot.usedMonthlyRequests()).isZero();
        assertThat(snapshot.usedPremiumRequests()).isZero();
        assertThat(snapshot.estimatedMonthlyCostEur()).isEqualByComparingTo(new BigDecimal("0.002000"));

        verify(userUsageRepository).findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD);
        verify(userUsageRepository).save(any(UserUsageEntity.class));
    }

    @Test
    void recordRequestIncrementsLifetimeRequestsForFreePlan() {
        UserUsageEntity existingUsage = new UserUsageEntity(
                TEST_USER_ID,
                PlanType.FREE,
                CURRENT_PERIOD
        );

        when(userUsageRepository.findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD))
                .thenReturn(Optional.of(existingUsage));

        usageService.recordRequest(PlanType.FREE, new BigDecimal("0.001000"), false);

        assertThat(existingUsage.getUsedLifetimeRequests()).isEqualTo(6);
        assertThat(existingUsage.getUsedMonthlyRequests()).isZero();
        assertThat(existingUsage.getUsedPremiumRequests()).isZero();
        assertThat(existingUsage.getEstimatedMonthlyCostEur()).isEqualByComparingTo(new BigDecimal("0.003000"));
    }

    @Test
    void recordRequestIncrementsMonthlyRequestsForPaidPlan() {
        UserUsageEntity existingUsage = new UserUsageEntity(
                TEST_USER_ID,
                PlanType.PLUS,
                CURRENT_PERIOD
        );

        when(userUsageRepository.findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD))
                .thenReturn(Optional.of(existingUsage));

        usageService.recordRequest(PlanType.PLUS, new BigDecimal("0.005000"), false);

        assertThat(existingUsage.getUsedLifetimeRequests()).isEqualTo(5);
        assertThat(existingUsage.getUsedMonthlyRequests()).isEqualTo(1);
        assertThat(existingUsage.getUsedPremiumRequests()).isZero();
        assertThat(existingUsage.getEstimatedMonthlyCostEur()).isEqualByComparingTo(new BigDecimal("0.007000"));
    }

    @Test
    void recordRequestIncrementsPremiumRequestsWhenPremiumRequest() {
        UserUsageEntity existingUsage = new UserUsageEntity(
                TEST_USER_ID,
                PlanType.PRO,
                CURRENT_PERIOD
        );

        when(userUsageRepository.findByUserIdAndPeriodYearMonth(TEST_USER_ID, CURRENT_PERIOD))
                .thenReturn(Optional.of(existingUsage));

        usageService.recordRequest(PlanType.PRO, new BigDecimal("0.020000"), true);

        assertThat(existingUsage.getUsedLifetimeRequests()).isEqualTo(5);
        assertThat(existingUsage.getUsedMonthlyRequests()).isEqualTo(1);
        assertThat(existingUsage.getUsedPremiumRequests()).isEqualTo(1);
        assertThat(existingUsage.getEstimatedMonthlyCostEur()).isEqualByComparingTo(new BigDecimal("0.022000"));
    }
}
