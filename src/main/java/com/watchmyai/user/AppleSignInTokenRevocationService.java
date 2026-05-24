package com.watchmyai.user;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.watchmyai.config.AppStoreServerProperties;
import com.watchmyai.config.AppleSignInServerProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppleSignInTokenRevocationService {

    private static final URI TOKEN_ENDPOINT = URI.create("https://appleid.apple.com/auth/token");
    private static final URI REVOKE_ENDPOINT = URI.create("https://appleid.apple.com/auth/revoke");
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration CLIENT_SECRET_LIFETIME = Duration.ofMinutes(5);

    private final AppleSignInServerProperties signInProperties;
    private final AppStoreServerProperties appStoreProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient httpClient;

    public AppleSignInTokenRevocationService(
            AppleSignInServerProperties signInProperties,
            AppStoreServerProperties appStoreProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.signInProperties = signInProperties;
        this.appStoreProperties = appStoreProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    public void revokeAuthorization(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IllegalArgumentException("Apple authorization code is required.");
        }

        String clientSecret = createClientSecret();
        JsonNode tokenPayload = postJson(TOKEN_ENDPOINT, Map.of(
                "client_id", appStoreProperties.bundleId(),
                "client_secret", clientSecret,
                "code", authorizationCode,
                "grant_type", "authorization_code"
        ));

        String tokenToRevoke = stringValue(tokenPayload, "refresh_token");
        String tokenType = "refresh_token";
        if (tokenToRevoke == null || tokenToRevoke.isBlank()) {
            tokenToRevoke = stringValue(tokenPayload, "access_token");
            tokenType = "access_token";
        }
        if (tokenToRevoke == null || tokenToRevoke.isBlank()) {
            throw new IllegalStateException("Apple did not return an authorization token to revoke.");
        }

        postNoContent(REVOKE_ENDPOINT, Map.of(
                "client_id", appStoreProperties.bundleId(),
                "client_secret", clientSecret,
                "token", tokenToRevoke,
                "token_type_hint", tokenType
        ));
    }

    private String createClientSecret() {
        try {
            Instant now = Instant.now(clock);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(signInProperties.teamId())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(CLIENT_SECRET_LIFETIME)))
                    .audience("https://appleid.apple.com")
                    .subject(appStoreProperties.bundleId())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signInProperties.keyId()).build(),
                    claims
            );
            jwt.sign(new ECDSASigner(readPrivateKey()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Apple client secret could not be created.", exception);
        }
    }

    private ECPrivateKey readPrivateKey() throws Exception {
        String pem = signInProperties.privateKey()
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(pem);
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private JsonNode postJson(URI uri, Map<String, String> fields) {
        HttpResponse<String> response = post(uri, fields);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Apple authorization code could not be exchanged.");
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Apple token response could not be read.", exception);
        }
    }

    private void postNoContent(URI uri, Map<String, String> fields) {
        HttpResponse<String> response = post(uri, fields);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Apple authorization could not be revoked.");
        }
    }

    private HttpResponse<String> post(URI uri, Map<String, String> fields) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(fields)))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new IllegalStateException("Apple authorization service could not be reached.", exception);
        }
    }

    private String formBody(Map<String, String> fields) {
        Map<String, String> orderedFields = new LinkedHashMap<>(fields);
        return orderedFields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String stringValue(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.stringValue();
    }
}
