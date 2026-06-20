package com.watchmyai.quota;

import com.watchmyai.config.CostGuardProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cross-user daily cost guard — the "kill-switch" that bounds aggregate OpenAI spend.
 *
 * <p>{@link #isOverDailyCap(PlanType)} is checked before a request reaches the provider;
 * {@link #recordSpend(BigDecimal)} is called after a successful call to grow the day's
 * running total. The check is eventually consistent (read-then-act, no row lock) which
 * is fine for a safety ceiling — a tiny overshoot from concurrency is acceptable, a
 * runaway bill is not.
 *
 * <p>By default only FREE requests are shed when the cap is hit (the unbounded-by-revenue
 * abuse vector); paying users keep working. See {@link CostGuardProperties}.
 */
@Service
public class GlobalCostGuardService {

    private static final Logger log = LoggerFactory.getLogger(GlobalCostGuardService.class);

    private final GlobalDailyCostRepository repository;
    private final CostGuardProperties properties;
    private final Clock clock;
    private final Counter blockedCounter;

    public GlobalCostGuardService(
            GlobalDailyCostRepository repository,
            CostGuardProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.blockedCounter = meterRegistry.counter("watchmyai.ai.cost_guard_blocked");
    }

    /**
     * Returns {@code true} when this request must be shed because today's aggregate spend
     * has reached the configured cap and this plan is subject to the guard.
     */
    @Transactional(readOnly = true)
    public boolean isOverDailyCap(PlanType planType) {
        if (!properties.enabled()) {
            return false;
        }
        BigDecimal cap = properties.globalDailyCapEur();
        if (cap == null || cap.signum() <= 0) {
            return false;
        }
        if (planType != PlanType.FREE && !properties.appliesToPaid()) {
            return false;
        }

        BigDecimal today = repository.findTotalForDay(currentDay()).orElse(BigDecimal.ZERO);
        boolean over = today.compareTo(cap) >= 0;
        if (over) {
            blockedCounter.increment();
            log.warn(
                    "Global daily cost guard tripped — shedding request. day={} spend={} cap={} plan={}",
                    currentDay(), today, cap, planType
            );
        }
        return over;
    }

    /** Adds a finalized request's cost to the day's running total. No-op for zero/negative. */
    @Transactional
    public void recordSpend(BigDecimal costEur) {
        if (costEur == null || costEur.signum() <= 0) {
            return;
        }
        repository.incrementForDay(currentDay(), costEur, Instant.now(clock));
    }

    private String currentDay() {
        return LocalDate.now(clock).toString();
    }
}
