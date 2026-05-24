package com.watchmyai.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeletionRequest(
        @NotBlank
        String identityToken,

        @NotBlank
        String authorizationCode,

        @NotBlank
        @Size(min = 22, max = 255)
        String nonce
) {
}
