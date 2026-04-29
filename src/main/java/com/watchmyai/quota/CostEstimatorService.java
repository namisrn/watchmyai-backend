package com.watchmyai.quota;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CostEstimatorService {

    private static final BigDecimal TOKENS_PER_MILLION = new BigDecimal("1000000");
    private static final BigDecimal USD_TO_EUR = new BigDecimal("0.92");

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
                .multiply(USD_TO_EUR)
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
        return switch (model) {
            case "gpt-5.4-nano" -> new BigDecimal("0.20");
            case "gpt-5.4-mini" -> new BigDecimal("0.75");
            case "gpt-5.4" -> new BigDecimal("2.50");
            case "gpt-5.5" -> new BigDecimal("5.00");
            default -> new BigDecimal("0.75");
        };
    }

    private BigDecimal outputCostPerMillionUsd(String model) {
        return switch (model) {
            case "gpt-5.4-nano" -> new BigDecimal("1.25");
            case "gpt-5.4-mini" -> new BigDecimal("4.50");
            case "gpt-5.4" -> new BigDecimal("15.00");
            case "gpt-5.5" -> new BigDecimal("30.00");
            default -> new BigDecimal("4.50");
        };
    }
}
