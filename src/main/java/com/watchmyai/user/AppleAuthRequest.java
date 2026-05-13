package com.watchmyai.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AppleAuthRequest(
        @NotBlank
        String identityToken,

        @NotBlank
        String authorizationCode,

        @NotBlank
        @Size(max = 255)
        String appleUserId,

        @NotBlank
        @Pattern(regexp = "ios|watch", message = "source must be one of: ios, watch")
        String source,

        @Size(max = 255)
        String deviceName
) {
}
