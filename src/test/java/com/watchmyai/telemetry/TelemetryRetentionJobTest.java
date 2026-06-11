package com.watchmyai.telemetry;

import com.watchmyai.common.DistributedLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-05-27T03:45:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DistributedLockService lock = directLock();

    @SuppressWarnings("unchecked")
    private static DistributedLockService directLock() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new DistributedLockService(provider);
    }

    @Test
    void purgesEventsOlderThanRetentionWindow() {
        TelemetryEventRepository repository = mock(TelemetryEventRepository.class);
        when(repository.purgeOlderThan(any())).thenReturn(128);
        TelemetryRetentionJob job = new TelemetryRetentionJob(repository, fixedClock, 365, lock);

        job.purgeExpiredEvents();

        Instant expectedThreshold = NOW.minus(365, ChronoUnit.DAYS);
        verify(repository).purgeOlderThan(expectedThreshold);
    }

    @Test
    void allowsConfigurableRetentionDays() {
        TelemetryEventRepository repository = mock(TelemetryEventRepository.class);
        TelemetryRetentionJob job = new TelemetryRetentionJob(repository, fixedClock, 90, lock);

        job.purgeExpiredEvents();

        Instant expectedThreshold = NOW.minus(90, ChronoUnit.DAYS);
        verify(repository).purgeOlderThan(expectedThreshold);
    }

    @Test
    void rejectsNonPositiveRetentionDays() {
        TelemetryEventRepository repository = mock(TelemetryEventRepository.class);

        assertThatThrownBy(() -> new TelemetryRetentionJob(repository, fixedClock, 0, lock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be >= 1");

        assertThatThrownBy(() -> new TelemetryRetentionJob(repository, fixedClock, -1, lock))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotThrowWhenNoRowsPurged() {
        TelemetryEventRepository repository = mock(TelemetryEventRepository.class);
        when(repository.purgeOlderThan(any())).thenReturn(0);
        TelemetryRetentionJob job = new TelemetryRetentionJob(repository, fixedClock, 365, lock);

        job.purgeExpiredEvents();

        assertThat(true).isTrue();
    }
}
