package com.watchmyai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppleAuthPropertiesTest {

    @Test
    void acceptsSingleConfiguredAudience() {
        AppleAuthProperties properties = new AppleAuthProperties(
                "https://appleid.apple.com",
                "https://appleid.apple.com/auth/keys",
                "com.sasanrafatnami.WatchMyAI"
        );

        assertThat(properties.hasAudience()).isTrue();
        assertThat(properties.acceptsAudience("com.sasanrafatnami.WatchMyAI")).isTrue();
        assertThat(properties.acceptsAudience("com.example.Other")).isFalse();
    }

    @Test
    void acceptsCommaSeparatedConfiguredAudiences() {
        AppleAuthProperties properties = new AppleAuthProperties(
                "https://appleid.apple.com",
                "https://appleid.apple.com/auth/keys",
                "com.sasanrafatnami.WatchMyAI, com.sasanrafatnami.WatchMyAI.watchkitapp"
        );

        assertThat(properties.acceptsAudience("com.sasanrafatnami.WatchMyAI")).isTrue();
        assertThat(properties.acceptsAudience("com.sasanrafatnami.WatchMyAI.watchkitapp")).isTrue();
    }

    @Test
    void ignoresBlankAudienceEntries() {
        AppleAuthProperties properties = new AppleAuthProperties(
                "https://appleid.apple.com",
                "https://appleid.apple.com/auth/keys",
                " , "
        );

        assertThat(properties.hasAudience()).isFalse();
    }
}
