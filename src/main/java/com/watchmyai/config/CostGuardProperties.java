package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Cross-user "cost kill-switch" configuration. Per-user caps (plan-catalog) bound a
 * single account; this bounds the AGGREGATE daily OpenAI spend so abuse, a pricing bug,
 * or a viral spike cannot run up an unbounded bill.
 *
 * <ul>
 *   <li>{@code enabled} — master switch.</li>
 *   <li>{@code global-daily-cap-eur} — once the day's total provider spend reaches this,
 *       further requests are shed. {@code <= 0} disables the cap.</li>
 *   <li>{@code applies-to-paid} — when {@code false} (default), only FREE requests are shed;
 *       paying users keep working (they are revenue and already bounded by per-user caps).
 *       Set {@code true} to make it a hard ceiling for everyone.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "watchmyai.cost-guard")
public class CostGuardProperties {

    private final boolean enabled;
    private final BigDecimal globalDailyCapEur;
    private final boolean appliesToPaid;

    public CostGuardProperties(boolean enabled, BigDecimal globalDailyCapEur, boolean appliesToPaid) {
        this.enabled = enabled;
        this.globalDailyCapEur = globalDailyCapEur;
        this.appliesToPaid = appliesToPaid;
    }

    public boolean enabled() {
        return enabled;
    }

    public BigDecimal globalDailyCapEur() {
        return globalDailyCapEur;
    }

    public boolean appliesToPaid() {
        return appliesToPaid;
    }
}
