package com.watchmyai.user;

import com.watchmyai.config.AppleAuthProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

@Service
public class AppleIdentityTokenVerifier {

    private static final String EXPECTED_ALGORITHM = "RS256";
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final AppleAuthProperties appleAuthProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;

    private volatile JsonNode cachedKeys;

    public AppleIdentityTokenVerifier(
            AppleAuthProperties appleAuthProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.appleAuthProperties = appleAuthProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AppleUserIdentity verify(String identityToken) {
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
        if (!appleAuthProperties.audience().equals(audience)) {
            throw new AuthenticationRequiredException("Apple identity token audience is invalid.");
        }

        Instant now = Instant.now(clock);
        long expiresAt = longClaim(claims, "exp");
        if (expiresAt <= now.minusSeconds(CLOCK_SKEW_SECONDS).getEpochSecond()) {
            throw new AuthenticationRequiredException("Apple identity token is expired.");
        }
    }

    private RSAPublicKey findPublicKey(String keyId) {
        JsonNode keys = getAppleKeys();
        RSAPublicKey publicKey = findPublicKey(keys, keyId);
        if (publicKey != null) {
            return publicKey;
        }

        cachedKeys = null;
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
        if (cachedKeys != null) {
            return cachedKeys;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(appleAuthProperties.jwksUrl())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AuthenticationRequiredException("Apple signing keys could not be loaded.");
            }

            cachedKeys = objectMapper.readTree(response.body()).get("keys");
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
