package com.watchmyai.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiPolicyValidatorTest {

    @Test
    void flagsConfiguredModelsWithoutPricing() {
        AiModelPolicyProperties modelPolicy = new AiModelPolicyProperties(
                "priced-model",
                "missing-premium",
                Map.of("translate", "missing-mode")
        );
        AiPricingProperties pricing = new AiPricingProperties(
                BigDecimal.ONE,
                "priced-model",
                Map.of("priced-model", new AiPricingProperties.ModelPrice(BigDecimal.ONE, BigDecimal.ONE))
        );

        AiPolicyValidator validator = new AiPolicyValidator(modelPolicy, pricing);

        assertThat(validator.validate()).containsExactly("missing-premium", "missing-mode");
    }
}
