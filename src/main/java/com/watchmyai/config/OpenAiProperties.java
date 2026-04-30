package com.watchmyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class OpenAiProperties {

    private final String apiKey;
    private final boolean mockEnabled;
    private final URI responsesUrl;

    public OpenAiProperties(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${watchmyai.openai.mock-enabled:${WATCHMYAI_OPENAI_MOCK_ENABLED:false}}") boolean mockEnabled,
            @Value("${watchmyai.openai.responses-url:https://api.openai.com/v1/responses}") String responsesUrl
    ) {
        this.apiKey = apiKey;
        this.mockEnabled = mockEnabled;
        this.responsesUrl = URI.create(responsesUrl);
    }

    public String apiKey() {
        return apiKey;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean mockEnabled() {
        return mockEnabled;
    }

    public URI responsesUrl() {
        return responsesUrl;
    }
}
