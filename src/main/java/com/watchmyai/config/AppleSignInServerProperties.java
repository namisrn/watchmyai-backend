package com.watchmyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppleSignInServerProperties {

    private final String teamId;
    private final String keyId;
    private final String privateKey;

    public AppleSignInServerProperties(
            @Value("${watchmyai.auth.apple.team-id:${APPLE_TEAM_ID:}}") String teamId,
            @Value("${watchmyai.auth.apple.signin-key-id:${APPLE_SIGNIN_KEY_ID:}}") String keyId,
            @Value("${watchmyai.auth.apple.signin-private-key:${APPLE_SIGNIN_PRIVATE_KEY:}}") String privateKey
    ) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.privateKey = privateKey;
    }

    public String privateKey() {
        return privateKey;
    }

    public String teamId() {
        return teamId;
    }

    public String keyId() {
        return keyId;
    }

    public boolean hasServerCredentials() {
        return teamId != null && !teamId.isBlank()
                && keyId != null && !keyId.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
