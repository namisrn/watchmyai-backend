package com.watchmyai.quota;

import com.watchmyai.config.PlanCatalogProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanConfigServiceTest {

    @Test
    void defaultsMatchReleasePaywallLimits() {
        PlanConfigService service = new PlanConfigService();

        PlanLimits free = service.getLimits(PlanType.FREE);

        assertThat(free.dailyRequestLimit()).isEqualTo(5);
        assertThat(free.monthlyRequestLimit()).isEqualTo(20);
        assertThat(free.monthlyCostCapEur()).isEqualByComparingTo("0.20");
    }

    @Test
    void configuredPlanCatalogOverridesDefaults() {
        PlanCatalogProperties properties = new PlanCatalogProperties(Map.of(
                PlanType.FREE,
                new PlanCatalogProperties.PlanDefinition(0, 8, 88, 0, 220, new BigDecimal("0.33"))
        ));
        PlanConfigService service = new PlanConfigService(properties);

        PlanLimits free = service.getLimits(PlanType.FREE);

        assertThat(free.dailyRequestLimit()).isEqualTo(8);
        assertThat(free.monthlyRequestLimit()).isEqualTo(88);
        assertThat(free.maxOutputTokens()).isEqualTo(220);
        assertThat(free.monthlyCostCapEur()).isEqualByComparingTo("0.33");
        assertThat(service.getLimits(PlanType.PLUS).monthlyRequestLimit()).isEqualTo(1000);
    }
}
