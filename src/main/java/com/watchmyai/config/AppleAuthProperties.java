package com.watchmyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AppleAuthProperties {

    private final String issuer;
    private final URI jwksUrl;
    private final Set<String> audiences;

    public AppleAuthProperties(
            @Value("${watchmyai.auth.apple.issuer:https://appleid.apple.com}") String issuer,
            @Value("${watchmyai.auth.apple.jwks-url:https://appleid.apple.com/auth/keys}") String jwksUrl,
            @Value("${watchmyai.auth.apple.audience:}") String audience
    ) {
        this.issuer = issuer;
        this.jwksUrl = URI.create(jwksUrl);
        this.audiences = Arrays.stream(audience.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String issuer() {
        return issuer;
    }

    public URI jwksUrl() {
        return jwksUrl;
    }

    public Set<String> audiences() {
        return audiences;
    }

    public boolean acceptsAudience(String audience) {
        return audience != null && audiences.contains(audience);
    }

    public boolean hasAudience() {
        return !audiences.isEmpty();
    }
}
