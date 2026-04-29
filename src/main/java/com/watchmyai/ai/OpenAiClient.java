package com.watchmyai.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.watchmyai.config.OpenAiProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
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

    public String ask(String model, String systemPrompt, String userPrompt, int maxOutputTokens) {
        if (!openAiProperties.hasApiKey()) {
            return "Mock-Antwort über OpenAiClient. Modell: " + model + ". Frage: " + userPrompt;
        }

        String requestBody = buildRequestBody(model, systemPrompt, userPrompt, maxOutputTokens);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/responses"))
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

            return extractOutputText(response.body());

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

    private String extractOutputText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

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
