package com.watchmyai.user;

import com.watchmyai.config.AppleAuthProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleIdentityTokenVerifierNonceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppleIdentityTokenVerifier verifier = new AppleIdentityTokenVerifier(
            new AppleAuthProperties(
                    "https://appleid.apple.com",
                    "https://appleid.apple.com/auth/keys",
                    "com.sasanrafatnami.WatchMyAI"
            ),
            objectMapper,
            Clock.systemUTC()
    );

    @Test
    void requiresClientAndTokenNonce() throws Exception {
        JsonNode claims = objectMapper.readTree("{\"nonce\":\"abc\"}");

        assertThatThrownBy(() -> invokeValidateNonce(claims, null))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Apple identity token nonce is required.");

        JsonNode claimsWithoutNonce = objectMapper.readTree("{}");
        assertThatThrownBy(() -> invokeValidateNonce(claimsWithoutNonce, "raw-nonce"))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Apple identity token nonce is required.");
    }

    @Test
    void acceptsSha256BoundNonce() throws Exception {
        String rawNonce = "raw-nonce";
        JsonNode claims = objectMapper.readTree("{\"nonce\":\"%s\"}".formatted(sha256Hex(rawNonce)));

        assertThatCode(() -> invokeValidateNonce(claims, rawNonce)).doesNotThrowAnyException();
    }

    private void invokeValidateNonce(JsonNode claims, String rawNonce) throws Exception {
        Method method = AppleIdentityTokenVerifier.class.getDeclaredMethod("validateNonce", JsonNode.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(verifier, claims, rawNonce);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
