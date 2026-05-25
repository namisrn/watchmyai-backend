package com.watchmyai.config;

import com.watchmyai.quota.PlanLimits;
import com.watchmyai.quota.PlanType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

@ConfigurationProperties(prefix = "watchmyai.plan-catalog")
public record PlanCatalogProperties(Map<PlanType, PlanDefinition> plans) {

    public PlanCatalogProperties {
        EnumMap<PlanType, PlanDefinition> merged = defaultPlanDefinitions();
        if (plans != null) {
            plans.forEach((planType, definition) -> {
                if (planType != null && definition != null) {
                    merged.put(planType, definition);
                }
            });
        }
        plans = Collections.unmodifiableMap(merged);
    }

    public static PlanCatalogProperties defaults() {
        return new PlanCatalogProperties(Map.of());
    }

    public PlanDefinition definitionFor(PlanType planType) {
        return plans.getOrDefault(planType, plans.get(PlanType.FREE));
    }

    public Map<PlanType, PlanLimits> toLimitsByPlan() {
        EnumMap<PlanType, PlanLimits> limits = new EnumMap<>(PlanType.class);
        for (PlanType planType : PlanType.values()) {
            limits.put(planType, definitionFor(planType).toLimits(planType));
        }
        return Collections.unmodifiableMap(limits);
    }

    private static EnumMap<PlanType, PlanDefinition> defaultPlanDefinitions() {
        EnumMap<PlanType, PlanDefinition> defaults = new EnumMap<>(PlanType.class);
        // Keep in sync with application.yaml plan-catalog defaults. Both must match,
        // because PlanConfigService falls back to these defaults when no YAML config
        // is present (unit tests, dev profiles, missing config files).
        defaults.put(PlanType.FREE, new PlanDefinition(0, 5, 20, 0, 180, new BigDecimal("0.10")));
        defaults.put(PlanType.PLUS, new PlanDefinition(0, 60, 500, 0, 300, new BigDecimal("1.20")));
        defaults.put(PlanType.PRO, new PlanDefinition(0, 150, 1000, 60, 400, new BigDecimal("2.80")));
        return defaults;
    }

    public record PlanDefinition(
            int lifetimeRequestLimit,
            int dailyRequestLimit,
            int monthlyRequestLimit,
            int monthlyPremiumRequestLimit,
            int maxOutputTokens,
            BigDecimal monthlyCostCapEur
    ) {
        public PlanDefinition {
            if (monthlyCostCapEur == null) {
                monthlyCostCapEur = BigDecimal.ZERO;
            }
        }

        PlanLimits toLimits(PlanType planType) {
            return new PlanLimits(
                    planType,
                    lifetimeRequestLimit,
                    dailyRequestLimit,
                    monthlyRequestLimit,
                    monthlyPremiumRequestLimit,
                    maxOutputTokens,
                    monthlyCostCapEur
            );
        }
    }
}
