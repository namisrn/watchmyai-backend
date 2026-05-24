package com.watchmyai.quota;

import com.watchmyai.config.AiPricingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CostEstimatorService {

    private static final BigDecimal TOKENS_PER_MILLION = new BigDecimal("1000000");

    private final AiPricingProperties pricingProperties;

    public CostEstimatorService(AiPricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    public BigDecimal estimateCostEur(String model, int inputTokens, int outputTokens) {
        BigDecimal inputCostPerMillionUsd = inputCostPerMillionUsd(model);
        BigDecimal outputCostPerMillionUsd = outputCostPerMillionUsd(model);

        BigDecimal inputCostUsd = BigDecimal.valueOf(inputTokens)
                .multiply(inputCostPerMillionUsd)
                .divide(TOKENS_PER_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal outputCostUsd = BigDecimal.valueOf(outputTokens)
                .multiply(outputCostPerMillionUsd)
                .divide(TOKENS_PER_MILLION, 12, RoundingMode.HALF_UP);

        return inputCostUsd
                .add(outputCostUsd)
                .multiply(pricingProperties.usdToEur())
                .setScale(6, RoundingMode.HALF_UP);
    }

    public int estimateInputTokens(String systemPrompt, String userPrompt) {
        return estimateTokens(systemPrompt) + estimateTokens(userPrompt);
    }

    public int estimateOutputTokens(String answer) {
        return estimateTokens(answer);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private BigDecimal inputCostPerMillionUsd(String model) {
        return pricingProperties.priceFor(model).inputUsdPerMillion();
    }

    private BigDecimal outputCostPerMillionUsd(String model) {
        return pricingProperties.priceFor(model).outputUsdPerMillion();
    }
}
