package com.watchmyai.user;

import com.watchmyai.config.AppleAuthProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AppleIdentityTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleIdentityTokenVerifier.class);
    private static final String EXPECTED_ALGORITHM = "RS256";
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final Duration JWKS_CACHE_TTL = Duration.ofHours(24);
    private static final Duration JWKS_HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final AppleAuthProperties appleAuthProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;

    private volatile JsonNode cachedKeys;
    private volatile Instant cachedKeysAt;

    public AppleIdentityTokenVerifier(
            AppleAuthProperties appleAuthProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.appleAuthProperties = appleAuthProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(JWKS_HTTP_TIMEOUT)
                .build();
    }

    public AppleUserIdentity verify(String identityToken, String rawNonce) {
        if (!appleAuthProperties.hasAudience()) {
            throw new AuthenticationRequiredException("Apple auth audience is not configured.");
        }

        String[] parts = identityToken.split("\\.");
        if (parts.length != 3) {
            throw new AuthenticationRequiredException("Invalid Apple identity token.");
        }

        try {
            JsonNode header = readJwtPart(parts[0]);
            JsonNode claims = readJwtPart(parts[1]);

            String algorithm = stringClaim(header, "alg");
            String keyId = stringClaim(header, "kid");
            if (!EXPECTED_ALGORITHM.equals(algorithm) || keyId == null || keyId.isBlank()) {
                throw new AuthenticationRequiredException("Unsupported Apple identity token.");
            }

            verifySignature(parts, findPublicKey(keyId));
            validateClaims(claims);
            validateNonce(claims, rawNonce);

            String subject = stringClaim(claims, "sub");
            if (subject == null || subject.isBlank()) {
                throw new AuthenticationRequiredException("Apple identity token has no subject.");
            }

            return new AppleUserIdentity(subject, stringClaim(claims, "email"));
        } catch (AuthenticationRequiredException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationRequiredException("Apple identity token could not be verified.", exception);
        }
    }

    private JsonNode readJwtPart(String encodedPart) {
        byte[] decoded = Base64.getUrlDecoder().decode(encodedPart);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private void verifySignature(String[] parts, RSAPublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new AuthenticationRequiredException("Apple identity token signature is invalid.");
            }
        } catch (AuthenticationRequiredException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthenticationRequiredException("Apple identity token signature could not be verified.", exception);
        }
    }

    private void validateClaims(JsonNode claims) {
        String issuer = stringClaim(claims, "iss");
        if (!appleAuthProperties.issuer().equals(issuer)) {
            throw new AuthenticationRequiredException("Apple identity token issuer is invalid.");
        }

        String audience = stringClaim(claims, "aud");
        if (!appleAuthProperties.acceptsAudience(audience)) {
            throw new AuthenticationRequiredException("Apple identity token audience is invalid.");
        }

        Instant now = Instant.now(clock);
        long expiresAt = longClaim(claims, "exp");
        if (expiresAt <= now.minusSeconds(CLOCK_SKEW_SECONDS).getEpochSecond()) {
            throw new AuthenticationRequiredException("Apple identity token is expired.");
        }
    }

    /**
     * Validates the nonce binding. The client passes {@code request.nonce = SHA256(rawNonce)} to
     * Apple, which echoes that value into the token's {@code nonce} claim. The backend receives the
     * raw nonce, hashes it and compares. Tolerant transition: when the client sends no nonce or the
     * token carries no nonce claim (older app versions), only a warning is logged.
     */
    private void validateNonce(JsonNode claims, String rawNonce) {
        String tokenNonce = stringClaim(claims, "nonce");
        boolean clientSentNonce = rawNonce != null && !rawNonce.isBlank();
        boolean tokenHasNonce = tokenNonce != null && !tokenNonce.isBlank();

        if (!clientSentNonce || !tokenHasNonce) {
            log.warn(
                    "Apple identity token verified without nonce binding clientSentNonce={} tokenHasNonce={}",
                    clientSentNonce,
                    tokenHasNonce
            );
            return;
        }

        if (!MessageDigest.isEqual(
                sha256Hex(rawNonce).getBytes(StandardCharsets.US_ASCII),
                tokenNonce.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new AuthenticationRequiredException("Apple identity token nonce is invalid.");
        }
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new AuthenticationRequiredException("Apple identity token nonce could not be hashed.", exception);
        }
    }

    private RSAPublicKey findPublicKey(String keyId) {
        JsonNode keys = getAppleKeys();
        RSAPublicKey publicKey = findPublicKey(keys, keyId);
        if (publicKey != null) {
            return publicKey;
        }

        cachedKeys = null;
        cachedKeysAt = null;
        publicKey = findPublicKey(getAppleKeys(), keyId);
        if (publicKey != null) {
            return publicKey;
        }

        throw new AuthenticationRequiredException("Apple signing key was not found.");
    }

    private RSAPublicKey findPublicKey(JsonNode keys, String keyId) {
        if (keys == null || !keys.isArray()) {
            return null;
        }

        for (JsonNode key : keys) {
            if (keyId.equals(stringClaim(key, "kid"))) {
                return toRsaPublicKey(key);
            }
        }

        return null;
    }

    private JsonNode getAppleKeys() {
        Instant now = Instant.now(clock);
        if (cachedKeys != null && cachedKeysAt != null && now.isBefore(cachedKeysAt.plus(JWKS_CACHE_TTL))) {
            return cachedKeys;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(appleAuthProperties.jwksUrl())
                    .timeout(JWKS_HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AuthenticationRequiredException("Apple signing keys could not be loaded.");
            }

            cachedKeys = objectMapper.readTree(response.body()).get("keys");
            cachedKeysAt = now;
            return cachedKeys;
        } catch (AuthenticationRequiredException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthenticationRequiredException("Apple signing keys could not be loaded.", exception);
        }
    }

    private RSAPublicKey toRsaPublicKey(JsonNode key) {
        try {
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                    unsignedBigInteger(stringClaim(key, "n")),
                    unsignedBigInteger(stringClaim(key, "e"))
            );

            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception exception) {
            throw new AuthenticationRequiredException("Apple signing key is invalid.", exception);
        }
    }

    private BigInteger unsignedBigInteger(String base64UrlValue) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64UrlValue));
    }

    private String stringClaim(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private long longClaim(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new AuthenticationRequiredException("Apple identity token is missing " + fieldName + ".");
        }

        return value.longValue();
    }
}
