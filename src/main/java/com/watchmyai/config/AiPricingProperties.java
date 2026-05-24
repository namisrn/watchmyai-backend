package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "watchmyai.ai.pricing")
public record AiPricingProperties(
        BigDecimal usdToEur,
        String defaultModel,
        Map<String, ModelPrice> models
) {

    public AiPricingProperties {
        if (usdToEur == null) {
            usdToEur = new BigDecimal("0.92");
        }
        if (defaultModel == null || defaultModel.isBlank()) {
            defaultModel = "gpt-5.4-mini";
        }

        Map<String, ModelPrice> merged = new HashMap<>(defaultModelPrices());
        if (models != null) {
            models.forEach((model, price) -> {
                if (model != null && !model.isBlank() && price != null) {
                    merged.put(model.trim(), price);
                }
            });
        }
        models = Collections.unmodifiableMap(merged);
    }

    public static AiPricingProperties defaults() {
        return new AiPricingProperties(null, null, Map.of());
    }

    public ModelPrice priceFor(String model) {
        ModelPrice configured = models.get(model);
        if (configured != null) {
            return configured;
        }
        return models.getOrDefault(defaultModel, new ModelPrice(new BigDecimal("0.75"), new BigDecimal("4.50")));
    }

    public boolean hasPriceFor(String model) {
        return model != null && models.containsKey(model);
    }

    private static Map<String, ModelPrice> defaultModelPrices() {
        return Map.of(
                "gpt-5.4-nano", new ModelPrice(new BigDecimal("0.20"), new BigDecimal("1.25")),
                "gpt-5.4-mini", new ModelPrice(new BigDecimal("0.75"), new BigDecimal("4.50")),
                "gpt-5.4", new ModelPrice(new BigDecimal("2.50"), new BigDecimal("15.00")),
                "gpt-5.5", new ModelPrice(new BigDecimal("5.00"), new BigDecimal("30.00"))
        );
    }

    public record ModelPrice(
            BigDecimal inputUsdPerMillion,
            BigDecimal outputUsdPerMillion
    ) {
        public ModelPrice {
            if (inputUsdPerMillion == null) {
                inputUsdPerMillion = BigDecimal.ZERO;
            }
            if (outputUsdPerMillion == null) {
                outputUsdPerMillion = BigDecimal.ZERO;
            }
        }
    }
}
