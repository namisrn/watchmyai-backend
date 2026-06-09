package com.watchmyai.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an Apple App Attest attestation cannot be verified (invalid/expired challenge,
 * failed certificate chain, mismatched team/bundle identifier, malformed payload). Surfaces
 * as HTTP 401 so the client can fall back to prompting Sign in with Apple.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AppAttestException extends RuntimeException {

    public AppAttestException(String message) {
        super(message);
    }

    public AppAttestException(String message, Throwable cause) {
        super(message, cause);
    }
}
