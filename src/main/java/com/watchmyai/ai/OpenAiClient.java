package com.watchmyai.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.watchmyai.common.api.RequestCorrelation;
import com.watchmyai.config.OpenAiProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private final OpenAiProperties openAiProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public OpenAiClient(OpenAiProperties openAiProperties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.circuitBreaker = CircuitBreaker.of("openai", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        // Retry budget: at most one extra attempt, and only for transient provider/network
        // failures (5xx, timeouts). Client errors such as 401/400 are never retried.
        this.retry = Retry.of("openai", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(500))
                .retryOnException(throwable ->
                        throwable instanceof OpenAiClientException openAiError
                                && openAiError.statusCode() >= 500)
                .build());
    }

    public OpenAiResponse ask(String model, String systemPrompt, String userPrompt, int maxOutputTokens) {
        if (!openAiProperties.hasApiKey()) {
            if (!openAiProperties.mockEnabled()) {
                throw new OpenAiClientException(AiUserFacingMessages.MISSING_API_KEY, 503);
            }

            String mockAnswer = AiUserFacingMessages.MOCK_ANSWER_PREFIX + " Model: " + model + ". Question: " + userPrompt;
            return new OpenAiResponse(
                    mockAnswer,
                    estimateTokens(systemPrompt) + estimateTokens(userPrompt),
                    estimateTokens(mockAnswer)
            );
        }

        Supplier<OpenAiResponse> resilientCall = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(
                        circuitBreaker,
                        () -> executeRequest(model, systemPrompt, userPrompt, maxOutputTokens)
                )
        );

        try {
            return resilientCall.get();
        } catch (CallNotPermittedException exception) {
            // The circuit breaker is open after repeated provider failures — fail fast instead
            // of tying up a worker thread on a call that is very likely to fail.
            throw new OpenAiClientException(AiUserFacingMessages.AI_PROVIDER_UNAVAILABLE, 503);
        }
    }

    private OpenAiResponse executeRequest(
            String model,
            String systemPrompt,
            String userPrompt,
            int maxOutputTokens
    ) {
        String requestBody = buildRequestBody(model, systemPrompt, userPrompt, maxOutputTokens);
        String requestId = currentRequestId();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(openAiProperties.responsesUrl())
                .header("Authorization", "Bearer " + openAiProperties.apiKey())
                .header("Content-Type", "application/json")
                .header("X-Client-Request-Id", requestId)
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error";
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn(
                        "OpenAI provider error status={} providerRequestId={}",
                        response.statusCode(),
                        response.headers().firstValue("x-request-id").orElse("missing")
                );
                throw new OpenAiClientException(
                        extractErrorMessage(response.body(), response.statusCode()),
                        response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String answer = extractOutputText(root, response.body());

            OpenAiResponse openAiResponse = new OpenAiResponse(
                    answer,
                    extractTokenCount(root, "input_tokens", estimateTokens(systemPrompt) + estimateTokens(userPrompt)),
                    extractTokenCount(root, "output_tokens", estimateTokens(answer))
            );
            outcome = "success";
            return openAiResponse;

        } catch (IOException exception) {
            throw new OpenAiClientException("OpenAI-Anfrage fehlgeschlagen.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiClientException("OpenAI-Anfrage wurde unterbrochen.", exception);
        } finally {
            sample.stop(meterRegistry.timer("watchmyai.openai.request", "outcome", outcome));
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

        log.warn(
                "OpenAI response without readable text. responseBodyLength={}",
                responseBody == null ? 0 : responseBody.length()
        );
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
                String providerMessage = sanitizeProviderMessage(message.stringValue());
                log.warn("OpenAI provider error {}: {}", statusCode, providerMessage);
                return "OpenAI-Fehler: " + providerMessage;
            }
        } catch (RuntimeException ignored) {
        }

        log.warn("OpenAI provider error {} without structured message.", statusCode);
        return "OpenAI-Fehler: " + statusCode;
    }

    private String currentRequestId() {
        String requestId = RequestCorrelation.currentRequestId();
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    static String sanitizeProviderMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage;
        message = message.replaceAll("sk-[A-Za-z0-9_\\-]+", "sk-***");
        message = message.replaceAll("Bearer\\s+[A-Za-z0-9_.-]+", "Bearer ***");
        return message;
    }
}
