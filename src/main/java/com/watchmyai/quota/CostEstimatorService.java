package com.watchmyai.quota;

import org.springframework.stereotype.Service;

@Service
public class CostEstimatorService {

    public double estimateCostEur(String model, int inputTokens, int outputTokens) {
        double inputCostPerMillionUsd = inputCostPerMillionUsd(model);
        double outputCostPerMillionUsd = outputCostPerMillionUsd(model);

        double costUsd = (inputTokens / 1_000_000.0 * inputCostPerMillionUsd)
                + (outputTokens / 1_000_000.0 * outputCostPerMillionUsd);

        double usdToEur = 0.92;
        return costUsd * usdToEur;
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

    private double inputCostPerMillionUsd(String model) {
        return switch (model) {
            case "gpt-5.4-nano" -> 0.20;
            case "gpt-5.4-mini" -> 0.75;
            case "gpt-5.4" -> 2.50;
            case "gpt-5.5" -> 5.00;
            default -> 0.75;
        };
    }

    private double outputCostPerMillionUsd(String model) {
        return switch (model) {
            case "gpt-5.4-nano" -> 1.25;
            case "gpt-5.4-mini" -> 4.50;
            case "gpt-5.4" -> 15.00;
            case "gpt-5.5" -> 30.00;
            default -> 4.50;
        };
    }
}
