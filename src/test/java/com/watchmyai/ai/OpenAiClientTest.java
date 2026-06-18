package com.watchmyai.ai;

import com.watchmyai.config.OpenAiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientTest {

    @Test
    void rejectsMissingApiKeyUnlessMockModeIsExplicitlyEnabled() {
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("", false, "https://api.openai.com/v1/responses"),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );

        assertThatThrownBy(() -> client.ask("gpt-5.4-mini", "System", "Hallo", 120))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessage(AiUserFacingMessages.MISSING_API_KEY)
                .extracting("statusCode")
                .isEqualTo(503);
    }

    @Test
    void keepsMockModeAvailableWhenExplicitlyEnabled() {
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("", true, "https://api.openai.com/v1/responses"),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );

        OpenAiResponse response = client.ask("gpt-5.4-mini", "System", "Hallo", 120);

        assertThat(response.answer()).contains(AiUserFacingMessages.MOCK_ANSWER_PREFIX);
    }

    @Test
    void disablesProviderStorageAndOmitsUserIdentifiers() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("sk-test", false, "https://api.openai.com/v1/responses"),
                objectMapper,
                new SimpleMeterRegistry()
        );

        String body = client.buildRequestBody("gpt-5.4-mini", "System", "Hallo", 120);
        JsonNode root = objectMapper.readTree(body);

        assertThat(root.size()).isEqualTo(5);
        assertThat(root.get("model").stringValue()).isEqualTo("gpt-5.4-mini");
        assertThat(root.get("instructions").stringValue()).isEqualTo("System");
        assertThat(root.get("input").stringValue()).isEqualTo("Hallo");
        assertThat(root.get("max_output_tokens").intValue()).isEqualTo(120);
        assertThat(root.get("store").booleanValue()).isFalse();
        assertThat(root.get("user")).isNull();
        assertThat(root.get("appleUserId")).isNull();
        assertThat(root.get("sessionToken")).isNull();
        assertThat(root.get("accountId")).isNull();
    }

    @Test
    void sanitizesProviderMessageWithApiKey() {
        String sanitized = OpenAiClient.sanitizeProviderMessage(
                "Incorrect API key provided: sk-abc123456789. Use Bearer sk-secret-token."
        );

        assertThat(sanitized).doesNotContain("sk-abc123456789");
        assertThat(sanitized).doesNotContain("sk-secret-token");
        assertThat(sanitized).contains("sk-***");
        assertThat(sanitized).contains("Bearer ***");
    }
}
