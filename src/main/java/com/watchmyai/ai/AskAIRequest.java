package com.watchmyai.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AskAIRequest(
        @NotBlank(message = "input must not be blank")
        @Size(max = 2_000, message = "input must not exceed 2000 characters")
        String input,

        @NotBlank(message = "source must not be blank")
        @Pattern(regexp = "watch|ios", message = "source must be one of: watch, ios")
        String source,

        @NotBlank(message = "mode must not be blank")
        @Pattern(
                regexp = "short_answer|translate|rewrite|explain|premium_reasoning",
                message = "mode must be one of: short_answer, translate, rewrite, explain, premium_reasoning"
        )
        String mode,

        @NotBlank(message = "language must not be blank")
        @Pattern(
                regexp = "auto|ar|cs|da|de|el|en|es|fr|hi|id|it|ja|ko|nb|nl|no|pl|pt-BR|ru|sv|th|tr|ur|vi|zh-Hans",
                message = "language must be a supported app language or auto"
        )
        String language,

        @NotBlank(message = "clientRequestId must not be blank")
        @Size(min = 8, max = 100, message = "clientRequestId must be between 8 and 100 characters")
        String clientRequestId
) {
}
