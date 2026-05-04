package com.watchmyai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                new AppStoreServerProperties(
                        "com.sasanrafatnami.WatchMyAI",
                        123456789L,
                        "issuer",
                        "key",
                        "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----",
                        "SANDBOX",
                        true
                )
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
                new AppStoreServerProperties(
                        "com.example.Other",
                        0L,
                        "",
                        "",
                        "",
                        "XCODE",
                        false
                )
        );

        assertThat(validator.validate())
                .contains(
                        "OPENAI_API_KEY must be set.",
                        "WATCHMYAI_OPENAI_MOCK_ENABLED must be false in prod.",
                        "APPLE_CLIENT_ID must be set to com.sasanrafatnami.WatchMyAI.",
                        "APP_STORE_BUNDLE_ID must be com.sasanrafatnami.WatchMyAI.",
                        "APP_STORE_APP_APPLE_ID must be set to the numeric App Store Connect app Apple ID.",
                        "APP_STORE_ISSUER_ID, APP_STORE_KEY_ID, and APP_STORE_PRIVATE_KEY must be set.",
                        "APP_STORE_ENVIRONMENT must be SANDBOX for TestFlight or PRODUCTION for release.",
                        "APP_STORE_VERIFICATION_ENABLED must be true in prod."
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
                new AppStoreServerProperties(
                        "com.sasanrafatnami.WatchMyAI",
                        123456789L,
                        "issuer",
                        "key",
                        "bad-key",
                        "PRODUCTION",
                        true
                )
        );

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production configuration is incomplete")
                .hasMessageContaining("OPENAI_API_KEY must be set.")
                .hasMessageContaining("APP_STORE_PRIVATE_KEY must contain the full .p8 private key");
    }
}
