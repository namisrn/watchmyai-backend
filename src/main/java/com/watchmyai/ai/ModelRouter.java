package com.watchmyai.ai;

import com.watchmyai.config.AiModelPolicyProperties;
import com.watchmyai.quota.PlanType;
import org.springframework.stereotype.Component;

@Component
public class ModelRouter {

    private final AiModelPolicyProperties modelPolicyProperties;

    public ModelRouter(AiModelPolicyProperties modelPolicyProperties) {
        this.modelPolicyProperties = modelPolicyProperties;
    }

    public String selectModel(AskAIRequest request, PlanType planType, boolean usePremiumModel) {
        if (planType == PlanType.PRO && usePremiumModel) {
            return modelPolicyProperties.proPremiumModel();
        }

        return modelPolicyProperties
                .modeModels()
                .getOrDefault(request.mode(), modelPolicyProperties.defaultModel());
    }
}
