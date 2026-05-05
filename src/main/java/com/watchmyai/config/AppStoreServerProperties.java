package com.watchmyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppStoreServerProperties {

    private final String bundleId;
    private final Long appAppleId;
    private final String issuerId;
    private final String keyId;
    private final String privateKey;
    private final String environment;
    private final boolean verificationEnabled;

    public AppStoreServerProperties(
            @Value("${watchmyai.app-store.bundle-id:${APP_STORE_BUNDLE_ID:com.sasanrafatnami.WatchMyAI}}") String bundleId,
            @Value("${watchmyai.app-store.app-apple-id:${APP_STORE_APP_APPLE_ID:0}}") Long appAppleId,
            @Value("${watchmyai.app-store.issuer-id:${APP_STORE_ISSUER_ID:}}") String issuerId,
            @Value("${watchmyai.app-store.key-id:${APP_STORE_KEY_ID:}}") String keyId,
            @Value("${watchmyai.app-store.private-key:${APP_STORE_PRIVATE_KEY:}}") String privateKey,
            @Value("${watchmyai.app-store.environment:${APP_STORE_ENVIRONMENT:SANDBOX}}") String environment,
            @Value("${watchmyai.app-store.verification-enabled:${APP_STORE_VERIFICATION_ENABLED:false}}") boolean verificationEnabled
    ) {
        this.bundleId = bundleId;
        this.appAppleId = appAppleId;
        this.issuerId = issuerId;
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.environment = environment;
        this.verificationEnabled = verificationEnabled;
    }

    public String bundleId() {
        return bundleId;
    }

    public Long appAppleId() {
        return appAppleId;
    }

    public String issuerId() {
        return issuerId;
    }

    public String keyId() {
        return keyId;
    }

    public String privateKey() {
        return privateKey;
    }

    public String environment() {
        return environment;
    }

    public boolean verificationEnabled() {
        return verificationEnabled;
    }

    public boolean hasServerApiCredentials() {
        return issuerId != null && !issuerId.isBlank()
                && keyId != null && !keyId.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }

    public boolean readyForProductionVerification() {
        return verificationEnabled && hasServerApiCredentials() && appAppleId != null && appAppleId > 0;
    }
}
