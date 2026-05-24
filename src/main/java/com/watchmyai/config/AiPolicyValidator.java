package com.watchmyai.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class AiPolicyValidator implements ApplicationRunner {

    private final AiModelPolicyProperties modelPolicyProperties;
    private final AiPricingProperties pricingProperties;

    public AiPolicyValidator(
            AiModelPolicyProperties modelPolicyProperties,
            AiPricingProperties pricingProperties
    ) {
        this.modelPolicyProperties = modelPolicyProperties;
        this.pricingProperties = pricingProperties;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public void run(ApplicationArguments args) {
        List<String> missingPrices = validate();
        if (!missingPrices.isEmpty()) {
            throw new IllegalStateException(
                    "AI model pricing is incomplete for configured models: " + String.join(", ", missingPrices)
            );
        }
    }

    List<String> validate() {
        Set<String> configuredModels = new LinkedHashSet<>();
        configuredModels.add(modelPolicyProperties.defaultModel());
        configuredModels.add(modelPolicyProperties.proPremiumModel());
        configuredModels.addAll(modelPolicyProperties.modeModels().values());

        List<String> missingPrices = new ArrayList<>();
        for (String model : configuredModels) {
            if (!pricingProperties.hasPriceFor(model)) {
                missingPrices.add(model);
            }
        }
        return missingPrices;
    }
}
