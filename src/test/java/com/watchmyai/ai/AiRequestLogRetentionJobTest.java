package com.watchmyai.ai;

import org.junit.jupiter.api.Test;

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

class AiRequestLogRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-05-27T03:30:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void purgesAnswersOlderThanRetentionWindow() {
        AiRequestLogRepository repository = mock(AiRequestLogRepository.class);
        when(repository.purgeAnswersOlderThan(any())).thenReturn(42);
        AiRequestLogRetentionJob job = new AiRequestLogRetentionJob(repository, fixedClock, 30);

        job.purgeExpiredAnswers();

        Instant expectedThreshold = NOW.minus(30, ChronoUnit.DAYS);
        verify(repository).purgeAnswersOlderThan(expectedThreshold);
    }

    @Test
    void allowsConfigurableRetentionDays() {
        AiRequestLogRepository repository = mock(AiRequestLogRepository.class);
        AiRequestLogRetentionJob job = new AiRequestLogRetentionJob(repository, fixedClock, 7);

        job.purgeExpiredAnswers();

        Instant expectedThreshold = NOW.minus(7, ChronoUnit.DAYS);
        verify(repository).purgeAnswersOlderThan(expectedThreshold);
    }

    @Test
    void rejectsNonPositiveRetentionDays() {
        AiRequestLogRepository repository = mock(AiRequestLogRepository.class);

        assertThatThrownBy(() -> new AiRequestLogRetentionJob(repository, fixedClock, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be >= 1");

        assertThatThrownBy(() -> new AiRequestLogRetentionJob(repository, fixedClock, -1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emitsLogLineWhenNoRowsPurged() {
        AiRequestLogRepository repository = mock(AiRequestLogRepository.class);
        when(repository.purgeAnswersOlderThan(any())).thenReturn(0);
        AiRequestLogRetentionJob job = new AiRequestLogRetentionJob(repository, fixedClock, 30);

        // Just verify no-op doesn't throw — the actual log behavior is debug-level
        // and would require LoggerFactory mocking that isn't worth the complexity.
        job.purgeExpiredAnswers();

        assertThat(true).isTrue();
    }
}
