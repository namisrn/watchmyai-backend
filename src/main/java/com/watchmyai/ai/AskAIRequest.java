package com.watchmyai.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AskAIRequest(
        @NotBlank
        @Size(max = 2_000)
        String input,

        @NotBlank
        @Pattern(regexp = "watch|ios")
        String source,

        @NotBlank
        @Pattern(regexp = "short_answer|translate|rewrite|explain|premium_reasoning")
        String mode,

        @NotBlank
        @Pattern(regexp = "de|en|auto")
        String language,

        @NotBlank
        @Size(min = 8, max = 100)
        String clientRequestId
) {
}