package com.watchmyai.ai;

import com.watchmyai.config.OpenAiProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientTest {

    @Test
    void rejectsMissingApiKeyUnlessMockModeIsExplicitlyEnabled() {
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("", false, "https://api.openai.com/v1/responses"),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> client.ask("gpt-5.4-mini", "System", "Hallo", 120))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessage("OPENAI_API_KEY fehlt im Backend.")
                .extracting("statusCode")
                .isEqualTo(503);
    }

    @Test
    void keepsMockModeAvailableWhenExplicitlyEnabled() {
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("", true, "https://api.openai.com/v1/responses"),
                new ObjectMapper()
        );

        OpenAiResponse response = client.ask("gpt-5.4-mini", "System", "Hallo", 120);

        assertThat(response.answer()).contains("Mock-Antwort");
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
