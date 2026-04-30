package com.watchmyai.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.watchmyai.config.OpenAiProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
public class OpenAiClient {

    private final OpenAiProperties openAiProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiClient(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public OpenAiResponse ask(String model, String systemPrompt, String userPrompt, int maxOutputTokens) {
        if (!openAiProperties.hasApiKey()) {
            if (!openAiProperties.mockEnabled()) {
                throw new OpenAiClientException("OPENAI_API_KEY fehlt im Backend.", 503);
            }

            String mockAnswer = "Mock-Antwort über OpenAiClient. Modell: " + model + ". Frage: " + userPrompt;
            return new OpenAiResponse(
                    mockAnswer,
                    estimateTokens(systemPrompt) + estimateTokens(userPrompt),
                    estimateTokens(mockAnswer)
            );
        }

        String requestBody = buildRequestBody(model, systemPrompt, userPrompt, maxOutputTokens);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(openAiProperties.responsesUrl())
                .header("Authorization", "Bearer " + openAiProperties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OpenAiClientException(
                        extractErrorMessage(response.body(), response.statusCode()),
                        response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String answer = extractOutputText(root, response.body());

            return new OpenAiResponse(
                    answer,
                    extractTokenCount(root, "input_tokens", estimateTokens(systemPrompt) + estimateTokens(userPrompt)),
                    extractTokenCount(root, "output_tokens", estimateTokens(answer))
            );

        } catch (IOException exception) {
            throw new OpenAiClientException("OpenAI-Anfrage fehlgeschlagen.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiClientException("OpenAI-Anfrage wurde unterbrochen.", exception);
        }
    }

    private String buildRequestBody(
            String model,
            String systemPrompt,
            String userPrompt,
            int maxOutputTokens
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "instructions", systemPrompt,
                    "input", userPrompt,
                    "max_output_tokens", maxOutputTokens
            ));
        } catch (RuntimeException exception) {
            throw new OpenAiClientException("OpenAI-Anfrage konnte nicht vorbereitet werden.", exception);
        }
    }

    private String extractOutputText(JsonNode root, String responseBody) {
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isString() && !outputText.stringValue().isBlank()) {
            return outputText.stringValue();
        }

        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode outputItem : output) {
                JsonNode content = outputItem.get("content");

                if (content != null && content.isArray()) {
                    for (JsonNode contentItem : content) {
                        JsonNode text = contentItem.get("text");

                        if (text != null && text.isString() && !text.stringValue().isBlank()) {
                            return text.stringValue();
                        }
                    }
                }
            }
        }

        System.out.println("OpenAI response without readable text: " + responseBody);
        throw new OpenAiClientException("OpenAI lieferte keine lesbare Antwort.", 502);
    }

    private int extractTokenCount(JsonNode root, String fieldName, int fallback) {
        JsonNode usage = root.get("usage");
        if (usage == null) {
            return fallback;
        }

        JsonNode tokenCount = usage.get(fieldName);
        if (tokenCount == null || !tokenCount.isNumber()) {
            return fallback;
        }

        return Math.max(0, tokenCount.intValue());
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("error").path("message");

            if (message.isString() && !message.stringValue().isBlank()) {
                return "OpenAI-Fehler: " + message.stringValue();
            }
        } catch (RuntimeException ignored) {
        }

        return "OpenAI-Fehler: " + statusCode;
    }
}
