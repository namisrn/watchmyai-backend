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

        String requestBody = """
                {
                  "model": "%s",
                  "instructions": "%s",
                  "input": "%s",
                  "max_output_tokens": %d
                }
                """.formatted(
                escapeJson(model),
                escapeJson(systemPrompt),
                escapeJson(userPrompt),
                maxOutputTokens
        );

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
                System.out.println("OpenAI error body: " + response.body());
                return "OpenAI-Fehler: " + response.statusCode();
            }

            return extractOutputText(response.body());

        } catch (IOException exception) {
            System.out.println("OpenAI IOException: " + exception.getMessage());
            return "OpenAI-Anfrage fehlgeschlagen.";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "OpenAI-Anfrage wurde unterbrochen.";
        }
    }

    private String extractOutputText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode outputItem : output) {
                JsonNode content = outputItem.get("content");

                if (content != null && content.isArray()) {
                    for (JsonNode contentItem : content) {
                        JsonNode text = contentItem.get("text");

                        if (text != null && text.isTextual() && !text.asText().isBlank()) {
                            return text.asText();
                        }
                    }
                }
            }
        }

        System.out.println("OpenAI response without readable text: " + responseBody);
        return "Keine lesbare Antwort erhalten.";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}