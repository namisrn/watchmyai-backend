package com.watchmyai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionSecretsValidatorTest {

    @Test
    void acceptsCompleteProductionConfiguration() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                new OpenAiProperties("sk-live", false, "https://api.openai.com/v1/responses"),
                new AppleAuthProperties(
                        "https://appleid.apple.com",
                        "https://appleid.apple.com/auth/keys",
                        "com.sasanrafatnami.WatchMyAI"
                ),
                new AppleSignInServerProperties(
                        "TEAMID1234",
                        "signin-key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----"
                ),
                new AppStoreServerProperties(
                        "com.sasanrafatnami.WatchMyAI",
                        123456789L,
                        "issuer",
                        "key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----",
                        "PRODUCTION",
                        true
                ),
                new RedisProperties("redis://redis:6379"),
                environment("prod")
        );

        assertThat(validator.validate()).isEmpty();
    }

    @Test
    void reportsAllRequiredProductionSecrets() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                new OpenAiProperties("", true, "https://api.openai.com/v1/responses"),
                new AppleAuthProperties(
                        "https://appleid.apple.com",
                        "https://appleid.apple.com/auth/keys",
                        ""
                ),
                new AppleSignInServerProperties("", "", ""),
                new AppStoreServerProperties(
                        "com.example.Other",
                        0L,
                        "",
                        "",
                        "",
                        "XCODE",
                        false
                ),
                new RedisProperties(""),
                environment("prod")
        );

        assertThat(validator.validate())
                .contains(
                        "OPENAI_API_KEY must be set.",
                        "WATCHMYAI_OPENAI_MOCK_ENABLED must be false in prod.",
                        "APPLE_CLIENT_ID must be set to com.sasanrafatnami.WatchMyAI.",
                        "APPLE_TEAM_ID, APPLE_SIGNIN_KEY_ID, and APPLE_SIGNIN_PRIVATE_KEY must be set.",
                        "APP_STORE_BUNDLE_ID must be com.sasanrafatnami.WatchMyAI.",
                        "APP_STORE_APP_APPLE_ID must be set to the numeric App Store Connect app Apple ID.",
                        "APP_STORE_ISSUER_ID, APP_STORE_KEY_ID, and APP_STORE_PRIVATE_KEY must be set.",
                        "APP_STORE_ENVIRONMENT must be PRODUCTION in prod; the production verifier also accepts Sandbox/TestFlight transactions through its fallback.",
                        "APP_STORE_VERIFICATION_ENABLED must be true in prod.",
                        "REDIS_URL must be set in prod."
                );
    }

    @Test
    void runFailsFastWithActionableMessage() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                new OpenAiProperties("", false, "https://api.openai.com/v1/responses"),
                new AppleAuthProperties(
                        "https://appleid.apple.com",
                        "https://appleid.apple.com/auth/keys",
                        "com.sasanrafatnami.WatchMyAI"
                ),
                new AppleSignInServerProperties(
                        "TEAMID1234",
                        "signin-key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----"
                ),
                new AppStoreServerProperties(
                        "com.sasanrafatnami.WatchMyAI",
                        123456789L,
                        "issuer",
                        "key",
                        "bad-key",
                        "PRODUCTION",
                        true
                ),
                new RedisProperties("redis://redis:6379"),
                environment("prod")
        );

        assertThatThrownBy(() -> validator.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production configuration is incomplete")
                .hasMessageContaining("OPENAI_API_KEY must be set.")
                .hasMessageContaining("APP_STORE_PRIVATE_KEY must contain the full .p8 private key");
    }

    @Test
    void rejectsDevelopmentProfilesInProduction() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                new OpenAiProperties("sk-live", false, "https://api.openai.com/v1/responses"),
                new AppleAuthProperties(
                        "https://appleid.apple.com",
                        "https://appleid.apple.com/auth/keys",
                        "com.sasanrafatnami.WatchMyAI"
                ),
                new AppleSignInServerProperties(
                        "TEAMID1234",
                        "signin-key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----"
                ),
                new AppStoreServerProperties(
                        "com.sasanrafatnami.WatchMyAI",
                        123456789L,
                        "issuer",
                        "key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----",
                        "PRODUCTION",
                        true
                ),
                new RedisProperties("redis://redis:6379"),
                environment("prod", "dev")
        );

        assertThat(validator.validate())
                .contains("SPRING_PROFILES_ACTIVE must not include dev or test in prod.");
    }

    @Test
    void rejectsSandboxAsPrimaryVerifierInProduction() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                new OpenAiProperties("sk-live", false, "https://api.openai.com/v1/responses"),
                new AppleAuthProperties(
                        "https://appleid.apple.com",
                        "https://appleid.apple.com/auth/keys",
                        "com.sasanrafatnami.WatchMyAI"
                ),
                new AppleSignInServerProperties(
                        "TEAMID1234",
                        "signin-key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----"
                ),
                new AppStoreServerProperties(
                        "com.sasanrafatnami.WatchMyAI",
                        123456789L,
                        "issuer",
                        "key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----",
                        "SANDBOX",
                        true
                ),
                new RedisProperties("redis://redis:6379"),
                environment("prod")
        );

        assertThat(validator.validate())
                .contains("APP_STORE_ENVIRONMENT must be PRODUCTION in prod; the production verifier also accepts Sandbox/TestFlight transactions through its fallback.");
    }

    private Environment environment(String... activeProfiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(activeProfiles);
        return environment;
    }
}
