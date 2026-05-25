package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "watchmyai.ai.model-policy")
public record AiModelPolicyProperties(
        String defaultModel,
        String proPremiumModel,
        Map<String, String> modeModels
) {

    public AiModelPolicyProperties {
        if (defaultModel == null || defaultModel.isBlank()) {
            defaultModel = "gpt-5.4-mini";
        }
        if (proPremiumModel == null || proPremiumModel.isBlank()) {
            proPremiumModel = "gpt-5.4";
        }

        Map<String, String> merged = new HashMap<>(defaultModeModels());
        if (modeModels != null) {
            modeModels.forEach((mode, model) -> {
                if (mode != null && !mode.isBlank() && model != null && !model.isBlank()) {
                    merged.put(mode.trim(), model.trim());
                }
            });
        }
        modeModels = Collections.unmodifiableMap(merged);
    }

    public static AiModelPolicyProperties defaults() {
        return new AiModelPolicyProperties(null, null, Map.of());
    }

    private static Map<String, String> defaultModeModels() {
        return Map.of(
                "translate", "gpt-5.4-nano",
                "rewrite", "gpt-5.4-nano",
                "explain", "gpt-5.4-mini",
                "short_answer", "gpt-5.4-mini",
                "premium_reasoning", "gpt-5.4-mini"
        );
    }
}
