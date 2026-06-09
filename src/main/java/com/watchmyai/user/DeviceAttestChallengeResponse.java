package com.watchmyai.user;

import java.time.Instant;

/**
 * Response of {@code POST /api/v1/device/attest/challenge}. The client hashes
 * {@code SHA-256(UTF-8(challenge))} into the {@code clientDataHash} passed to
 * {@code DCAppAttestService.attestKey}.
 */
public record DeviceAttestChallengeResponse(
        String challenge,
        Instant expiresAt
) {
}
