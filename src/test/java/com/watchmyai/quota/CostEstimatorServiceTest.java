package com.watchmyai.quota;

import com.watchmyai.config.AiPricingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CostEstimatorServiceTest {

    @Test
    void usesConfiguredModelPricingAndFxRate() {
        AiPricingProperties properties = new AiPricingProperties(
                BigDecimal.ONE,
                "custom-mini",
                Map.of("custom-mini", new AiPricingProperties.ModelPrice(
                        new BigDecimal("1.00"),
                        new BigDecimal("2.00")
                ))
        );
        CostEstimatorService service = new CostEstimatorService(properties);

        BigDecimal cost = service.estimateCostEur("custom-mini", 1_000_000, 500_000);

        assertThat(cost).isEqualByComparingTo("2.000000");
    }

    @Test
    void fallsBackToDefaultModelPriceForUnknownModel() {
        CostEstimatorService service = new CostEstimatorService(AiPricingProperties.defaults());

        BigDecimal cost = service.estimateCostEur("unknown-model", 1_000_000, 0);

        assertThat(cost).isEqualByComparingTo("0.690000");
    }
}
