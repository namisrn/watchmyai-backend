package com.watchmyai.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@Profile("prod")
public class ProductionSecretsValidator implements ApplicationRunner {

    private static final String EXPECTED_APPLE_CLIENT_ID = "com.sasanrafatnami.WatchMyAI";
    private static final String EXPECTED_BUNDLE_ID = "com.sasanrafatnami.WatchMyAI";

    private final OpenAiProperties openAiProperties;
    private final AppleAuthProperties appleAuthProperties;
    private final AppleSignInServerProperties appleSignInServerProperties;
    private final AppStoreServerProperties appStoreServerProperties;
    private final RedisProperties redisProperties;
    private final Environment environment;

    public ProductionSecretsValidator(
            OpenAiProperties openAiProperties,
            AppleAuthProperties appleAuthProperties,
            AppleSignInServerProperties appleSignInServerProperties,
            AppStoreServerProperties appStoreServerProperties,
            RedisProperties redisProperties,
            Environment environment
    ) {
        this.openAiProperties = openAiProperties;
        this.appleAuthProperties = appleAuthProperties;
        this.appleSignInServerProperties = appleSignInServerProperties;
        this.appStoreServerProperties = appStoreServerProperties;
        this.redisProperties = redisProperties;
        this.environment = environment;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public void run(ApplicationArguments args) {
        List<String> errors = validate();
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Production configuration is incomplete:\n- " + String.join("\n- ", errors)
            );
        }
    }

    List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (!openAiProperties.hasApiKey()) {
            errors.add("OPENAI_API_KEY must be set.");
        }
        if (openAiProperties.mockEnabled()) {
            errors.add("WATCHMYAI_OPENAI_MOCK_ENABLED must be false in prod.");
        }
        if (hasActiveProfile("dev") || hasActiveProfile("test")) {
            errors.add("SPRING_PROFILES_ACTIVE must not include dev or test in prod.");
        }

        if (!appleAuthProperties.hasAudience()) {
            errors.add("APPLE_CLIENT_ID must be set to " + EXPECTED_APPLE_CLIENT_ID + ".");
        } else if (!appleAuthProperties.acceptsAudience(EXPECTED_APPLE_CLIENT_ID)) {
            errors.add("APPLE_CLIENT_ID must include " + EXPECTED_APPLE_CLIENT_ID + ".");
        }
        if (!appleSignInServerProperties.hasServerCredentials()) {
            errors.add("APPLE_TEAM_ID, APPLE_SIGNIN_KEY_ID, and APPLE_SIGNIN_PRIVATE_KEY must be set.");
        } else if (!looksLikePrivateKey(appleSignInServerProperties.privateKey())) {
            errors.add("APPLE_SIGNIN_PRIVATE_KEY must contain the full .p8 private key including BEGIN/END PRIVATE KEY.");
        }

        if (!EXPECTED_BUNDLE_ID.equals(appStoreServerProperties.bundleId())) {
            errors.add("APP_STORE_BUNDLE_ID must be " + EXPECTED_BUNDLE_ID + ".");
        }
        if (appStoreServerProperties.appAppleId() == null || appStoreServerProperties.appAppleId() <= 0) {
            errors.add("APP_STORE_APP_APPLE_ID must be set to the numeric App Store Connect app Apple ID.");
        }
        if (!appStoreServerProperties.hasServerApiCredentials()) {
            errors.add("APP_STORE_ISSUER_ID, APP_STORE_KEY_ID, and APP_STORE_PRIVATE_KEY must be set.");
        } else if (!looksLikePrivateKey(appStoreServerProperties.privateKey())) {
            errors.add("APP_STORE_PRIVATE_KEY must contain the full .p8 private key including BEGIN/END PRIVATE KEY.");
        }
        if (!isDeployableAppStoreEnvironment(appStoreServerProperties.environment())) {
            errors.add("APP_STORE_ENVIRONMENT must be SANDBOX for TestFlight or PRODUCTION for release.");
        }
        if (!appStoreServerProperties.verificationEnabled()) {
            errors.add("APP_STORE_VERIFICATION_ENABLED must be true in prod.");
        }
        if (!redisProperties.hasUrl()) {
            errors.add("REDIS_URL must be set in prod.");
        }

        return errors;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean looksLikePrivateKey(String privateKey) {
        if (privateKey == null || privateKey.isBlank()) {
            return false;
        }

        String normalized = privateKey.replace("\\n", "\n");
        return normalized.contains("-----BEGIN PRIVATE KEY-----")
                && normalized.contains("-----END PRIVATE KEY-----");
    }

    private boolean isDeployableAppStoreEnvironment(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("SANDBOX") || normalized.equals("PRODUCTION");
    }

    private boolean hasActiveProfile(String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }
}
