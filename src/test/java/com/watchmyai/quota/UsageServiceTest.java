package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
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
    private UsageService usageService;

    @BeforeEach
    void setUp() {
        userUsageRepository = mock(UserUsageRepository.class);
        UserContextService userContextService = mock(UserContextService.class);
        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity(TEST_USER_ID));

        usageService = new UsageService(userUsageRepository, userContextService, FIXED_CLOCK);
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
        assertThat(snapshot.estimatedMonthlyCostEur()).isCloseTo(0.002, offset(0.000001));

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

        usageService.recordRequest(PlanType.FREE, 0.001, false);

        assertThat(existingUsage.getUsedLifetimeRequests()).isEqualTo(6);
        assertThat(existingUsage.getUsedMonthlyRequests()).isZero();
        assertThat(existingUsage.getUsedPremiumRequests()).isZero();
        assertThat(existingUsage.getEstimatedMonthlyCostEur()).isCloseTo(0.003, offset(0.000001));
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

        usageService.recordRequest(PlanType.PLUS, 0.005, false);

        assertThat(existingUsage.getUsedLifetimeRequests()).isEqualTo(5);
        assertThat(existingUsage.getUsedMonthlyRequests()).isEqualTo(1);
        assertThat(existingUsage.getUsedPremiumRequests()).isZero();
        assertThat(existingUsage.getEstimatedMonthlyCostEur()).isCloseTo(0.007, offset(0.000001));
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

        usageService.recordRequest(PlanType.PRO, 0.02, true);

        assertThat(existingUsage.getUsedLifetimeRequests()).isEqualTo(5);
        assertThat(existingUsage.getUsedMonthlyRequests()).isEqualTo(1);
        assertThat(existingUsage.getUsedPremiumRequests()).isEqualTo(1);
        assertThat(existingUsage.getEstimatedMonthlyCostEur()).isCloseTo(0.022, offset(0.000001));
    }
}
