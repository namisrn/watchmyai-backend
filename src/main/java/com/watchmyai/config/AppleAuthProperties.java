package com.watchmyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class AppleAuthProperties {

    private final String issuer;
    private final URI jwksUrl;
    private final String audience;

    public AppleAuthProperties(
            @Value("${watchmyai.auth.apple.issuer:https://appleid.apple.com}") String issuer,
            @Value("${watchmyai.auth.apple.jwks-url:https://appleid.apple.com/auth/keys}") String jwksUrl,
            @Value("${watchmyai.auth.apple.audience:}") String audience
    ) {
        this.issuer = issuer;
        this.jwksUrl = URI.create(jwksUrl);
        this.audience = audience;
    }

    public String issuer() {
        return issuer;
    }

    public URI jwksUrl() {
        return jwksUrl;
    }

    public String audience() {
        return audience;
    }

    public boolean hasAudience() {
        return audience != null && !audience.isBlank();
    }
}
