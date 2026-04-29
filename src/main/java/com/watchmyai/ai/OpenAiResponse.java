package com.watchmyai.ai;

public record OpenAiResponse(
        String answer,
        int inputTokens,
        int outputTokens
) {
}
