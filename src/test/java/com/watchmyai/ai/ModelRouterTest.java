package com.watchmyai.ai;

import com.watchmyai.config.AiModelPolicyProperties;
import com.watchmyai.quota.PlanType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRouterTest {

    @Test
    void routesByConfiguredModePolicy() {
        ModelRouter router = new ModelRouter(new AiModelPolicyProperties(
                "default-model",
                "premium-model",
                Map.of("translate", "cheap-model")
        ));

        AskAIRequest translate = new AskAIRequest("Hallo", "watch", "translate", "de", "request-12345");
        AskAIRequest explain = new AskAIRequest("Hallo", "watch", "explain", "de", "request-67890");

        assertThat(router.selectModel(translate, PlanType.PLUS, false)).isEqualTo("cheap-model");
        assertThat(router.selectModel(explain, PlanType.PLUS, false)).isEqualTo("gpt-5.4-mini");
    }

    @Test
    void proPremiumRequestsUseConfiguredPremiumModel() {
        ModelRouter router = new ModelRouter(new AiModelPolicyProperties(
                "default-model",
                "premium-model",
                Map.of()
        ));
        AskAIRequest request = new AskAIRequest("Hallo", "watch", "premium_reasoning", "de", "request-12345");

        assertThat(router.selectModel(request, PlanType.PRO, true)).isEqualTo("premium-model");
    }
}
