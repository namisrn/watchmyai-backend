package com.watchmyai.quota;

import com.watchmyai.config.CostGuardProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalCostGuardServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-12T10:00:00Z"), ZoneOffset.UTC);
    private static final String DAY = "2026-06-12";

    private GlobalCostGuardService newGuard(GlobalDailyCostRepository repo, CostGuardProperties props) {
        return new GlobalCostGuardService(repo, props, CLOCK, new SimpleMeterRegistry());
    }

    private CostGuardProperties props(boolean enabled, String cap, boolean appliesToPaid) {
        return new CostGuardProperties(enabled, new BigDecimal(cap), appliesToPaid);
    }

    @Test
    void disabledGuardNeverSheds() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        GlobalCostGuardService guard = newGuard(repo, props(false, "1.00", true));

        assertThat(guard.isOverDailyCap(PlanType.FREE)).isFalse();
        verify(repo, never()).findTotalForDay(any());
    }

    @Test
    void nonPositiveCapDisablesShedding() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        GlobalCostGuardService guard = newGuard(repo, props(true, "0.00", true));

        assertThat(guard.isOverDailyCap(PlanType.FREE)).isFalse();
        verify(repo, never()).findTotalForDay(any());
    }

    @Test
    void shedsFreeWhenDailyTotalReachesCap() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        when(repo.findTotalForDay(DAY)).thenReturn(Optional.of(new BigDecimal("15.00")));
        GlobalCostGuardService guard = newGuard(repo, props(true, "15.00", false));

        assertThat(guard.isOverDailyCap(PlanType.FREE)).isTrue();
    }

    @Test
    void allowsFreeWhenDailyTotalBelowCap() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        when(repo.findTotalForDay(DAY)).thenReturn(Optional.of(new BigDecimal("14.999999")));
        GlobalCostGuardService guard = newGuard(repo, props(true, "15.00", false));

        assertThat(guard.isOverDailyCap(PlanType.FREE)).isFalse();
    }

    @Test
    void paidIsExemptByDefaultEvenOverCap() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        when(repo.findTotalForDay(DAY)).thenReturn(Optional.of(new BigDecimal("999.00")));
        GlobalCostGuardService guard = newGuard(repo, props(true, "15.00", false));

        assertThat(guard.isOverDailyCap(PlanType.PLUS)).isFalse();
        assertThat(guard.isOverDailyCap(PlanType.PRO)).isFalse();
        // Paid is exempt without even consulting the ledger.
        verify(repo, never()).findTotalForDay(any());
    }

    @Test
    void paidIsShedWhenAppliesToPaidEnabled() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        when(repo.findTotalForDay(DAY)).thenReturn(Optional.of(new BigDecimal("20.00")));
        GlobalCostGuardService guard = newGuard(repo, props(true, "15.00", true));

        assertThat(guard.isOverDailyCap(PlanType.PRO)).isTrue();
    }

    @Test
    void emptyLedgerDayIsTreatedAsZeroSpend() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        when(repo.findTotalForDay(DAY)).thenReturn(Optional.empty());
        GlobalCostGuardService guard = newGuard(repo, props(true, "15.00", false));

        assertThat(guard.isOverDailyCap(PlanType.FREE)).isFalse();
    }

    @Test
    void recordSpendIncrementsLedgerForPositiveCostOnly() {
        GlobalDailyCostRepository repo = mock(GlobalDailyCostRepository.class);
        GlobalCostGuardService guard = newGuard(repo, props(true, "15.00", false));

        guard.recordSpend(new BigDecimal("0.001100"));
        verify(repo).incrementForDay(eq(DAY), eq(new BigDecimal("0.001100")), any(Instant.class));

        guard.recordSpend(BigDecimal.ZERO);
        guard.recordSpend(null);
        // No further increments for zero / null.
        verify(repo).incrementForDay(any(), any(), any());
    }
}
