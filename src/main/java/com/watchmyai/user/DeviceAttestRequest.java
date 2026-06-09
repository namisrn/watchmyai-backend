package com.watchmyai.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/device/attest}. {@code keyId} and {@code attestationObject} are
 * standard Base64; {@code challenge} is the exact string returned by the challenge endpoint.
 */
public record DeviceAttestRequest(
        @NotBlank
        @Size(max = 1024)
        String keyId,

        @NotBlank
        @Size(max = 16384)
        String attestationObject,

        @NotBlank
        @Size(max = 255)
        String challenge,

        @NotBlank
        @Pattern(regexp = "ios|watch", message = "source must be one of: ios, watch")
        String source,

        @Size(max = 255)
        String deviceName
) {
}
