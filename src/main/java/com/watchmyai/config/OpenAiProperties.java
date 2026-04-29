package com.watchmyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class OpenAiProperties {

    private final String apiKey;
    private final URI responsesUrl;

    public OpenAiProperties(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${watchmyai.openai.responses-url:https://api.openai.com/v1/responses}") String responsesUrl
    ) {
        this.apiKey = apiKey;
        this.responsesUrl = URI.create(responsesUrl);
    }

    public String apiKey() {
        return apiKey;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public URI responsesUrl() {
        return responsesUrl;
    }
}
