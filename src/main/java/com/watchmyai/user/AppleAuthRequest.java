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
        String deviceName,

        // Apple's nonce is a Base64URL SHA-256 hash of the client's raw nonce — at least 22
        // chars (16 random bytes → 22 base64-url chars without padding). Required for replay
        // protection; `AppleIdentityTokenVerifier.validateNonce` rejects blank values anyway,
        // but `@NotBlank` surfaces the error at DTO validation instead of after JWS parsing.
        @NotBlank
        @Size(min = 22, max = 255)
        String nonce
) {
}
