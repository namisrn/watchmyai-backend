package com.watchmyai.ai;

import jakarta.validation.constraints.NotBlank;

public record AskAIRequest(
        @NotBlank String input,
        @NotBlank String source,
        @NotBlank String mode,
        @NotBlank String language,
        @NotBlank String clientRequestId
) {
}