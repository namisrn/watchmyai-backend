package com.watchmyai.ai;

import com.watchmyai.quota.PlanType;
import org.springframework.stereotype.Component;

@Component
public class ModelRouter {

    public String selectModel(AskAIRequest request, PlanType planType, boolean usePremiumModel) {
        if (planType == PlanType.PRO && usePremiumModel) {
            return "gpt-5.4";
        }

        return switch (request.mode()) {
            case "translate", "rewrite" -> "gpt-5.4-nano";
            case "explain", "short_answer", "premium_reasoning" -> "gpt-5.4-mini";
            default -> "gpt-5.4-mini";
        };
    }
}