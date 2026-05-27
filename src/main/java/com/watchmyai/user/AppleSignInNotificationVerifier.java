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
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies and parses Apple Sign-In server-to-server notification JWTs.
 *
 * <p>Apple POSTs a JWS-signed payload to the configured server notification URL
 * whenever a user revokes Sign-in-with-Apple, deletes their Apple ID, or toggles
 * private-email forwarding. The JWT uses the SAME signing keys as the identity
 * token used at sign-in (https://appleid.apple.com/auth/keys), but the claims
 * shape is different:
 *
 * <ul>
 *   <li>{@code aud} = our app's bundle ID</li>
 *   <li>{@code iss} = "https://appleid.apple.com"</li>
 *   <li>{@code events} = a STRING containing JSON (nested) with the actual event</li>
 * </ul>
 *
 * We can't reuse {@link AppleIdentityTokenVerifier} verbatim because it requires
 * a client-supplied nonce and the wrapped {@code events} JSON-in-string shape.
 * The JWKS-fetching half is duplicated for clarity — a future refactor can
 * promote {@code AppleJwksCache} as a shared component when a third Apple-JWT
 * use-case appears.
 */
@Service
public class AppleSignInNotificationVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleSignInNotificationVerifier.class);
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

    public AppleSignInNotificationVerifier(
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

    /**
     * Verify the JWT envelope (signature, issuer, audience, freshness) and parse
     * the inner {@code events} claim. Throws {@link AppleSignInNotificationException}
     * with a stable code on any verification failure so the controller can return
     * 400 vs 401 vs 500 deterministically.
     */
    public AppleSignInNotificationEvent verify(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank()) {
            throw new AppleSignInNotificationException("signedPayload is missing");
        }
        if (!appleAuthProperties.hasAudience()) {
            throw new AppleSignInNotificationException("Apple auth audience is not configured.");
        }

        String[] parts = signedPayload.split("\\.");
        if (parts.length != 3) {
            throw new AppleSignInNotificationException("Notification JWT is malformed.");
        }

        try {
            JsonNode header = readJwtPart(parts[0]);
            JsonNode claims = readJwtPart(parts[1]);

            String algorithm = stringClaim(header, "alg");
            String keyId = stringClaim(header, "kid");
            if (!EXPECTED_ALGORITHM.equals(algorithm) || keyId == null || keyId.isBlank()) {
                throw new AppleSignInNotificationException("Notification JWT has unsupported header.");
            }

            verifySignature(parts, findPublicKey(keyId));
            validateClaims(claims);

            return parseEvents(claims);
        } catch (AppleSignInNotificationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppleSignInNotificationException("Notification JWT could not be verified.", exception);
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
                throw new AppleSignInNotificationException("Notification JWT signature is invalid.");
            }
        } catch (AppleSignInNotificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppleSignInNotificationException("Notification JWT signature could not be verified.", exception);
        }
    }

    private void validateClaims(JsonNode claims) {
        String issuer = stringClaim(claims, "iss");
        if (!appleAuthProperties.issuer().equals(issuer)) {
            throw new AppleSignInNotificationException("Notification JWT issuer is invalid: " + issuer);
        }

        String audience = stringClaim(claims, "aud");
        if (!appleAuthProperties.acceptsAudience(audience)) {
            throw new AppleSignInNotificationException("Notification JWT audience is invalid: " + audience);
        }

        // `iat` (issued-at) is the only timestamp Apple ships on server-to-server
        // notifications. Reject anything claiming to be from the far future
        // (clock skew + 60s window) — keeps tampered timestamps from being
        // accepted while still allowing reasonable delivery latency.
        Long issuedAt = longClaimOrNull(claims, "iat");
        if (issuedAt != null) {
            Instant now = Instant.now(clock);
            if (issuedAt > now.plusSeconds(CLOCK_SKEW_SECONDS).getEpochSecond()) {
                throw new AppleSignInNotificationException("Notification JWT iat is in the future.");
            }
        }
    }

    private AppleSignInNotificationEvent parseEvents(JsonNode claims) {
        String eventsRaw = stringClaim(claims, "events");
        if (eventsRaw == null || eventsRaw.isBlank()) {
            throw new AppleSignInNotificationException("Notification JWT is missing events claim.");
        }

        JsonNode events;
        try {
            events = objectMapper.readTree(eventsRaw);
        } catch (RuntimeException exception) {
            throw new AppleSignInNotificationException("Notification JWT events payload is not JSON.", exception);
        }

        String type = stringClaim(events, "type");
        String subject = stringClaim(events, "sub");
        if (subject == null || subject.isBlank()) {
            throw new AppleSignInNotificationException("Notification JWT events is missing sub.");
        }

        Long eventTimeMillis = longClaimOrNull(events, "event_time");
        Instant eventTime = eventTimeMillis != null
                ? Instant.ofEpochMilli(eventTimeMillis)
                : Instant.now(clock);

        return new AppleSignInNotificationEvent(
                AppleSignInNotificationEvent.Type.fromValue(type),
                subject,
                stringClaim(events, "email"),
                eventTime
        );
    }

    private RSAPublicKey findPublicKey(String keyId) {
        JsonNode keys = getAppleKeys();
        RSAPublicKey publicKey = findPublicKey(keys, keyId);
        if (publicKey != null) {
            return publicKey;
        }

        // Apple rotates keys; bust the cache once and try again before failing.
        cachedKeys = null;
        cachedKeysAt = null;
        publicKey = findPublicKey(getAppleKeys(), keyId);
        if (publicKey != null) {
            return publicKey;
        }

        throw new AppleSignInNotificationException("Apple signing key was not found.");
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
                throw new AppleSignInNotificationException("Apple signing keys could not be loaded.");
            }

            cachedKeys = objectMapper.readTree(response.body()).get("keys");
            cachedKeysAt = now;
            return cachedKeys;
        } catch (AppleSignInNotificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppleSignInNotificationException("Apple signing keys could not be loaded.", exception);
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
            throw new AppleSignInNotificationException("Apple signing key is invalid.", exception);
        }
    }

    private BigInteger unsignedBigInteger(String base64UrlValue) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64UrlValue));
    }

    private String stringClaim(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode value = node.get(fieldName);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private Long longClaimOrNull(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.longValue();
    }
}
