package com.watchmyai.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AppleSignInNotificationEventTest {

    @Test
    void mapsKnownEventTypes() {
        assertThat(AppleSignInNotificationEvent.Type.fromValue("consent-revoked"))
                .isEqualTo(AppleSignInNotificationEvent.Type.CONSENT_REVOKED);
        assertThat(AppleSignInNotificationEvent.Type.fromValue("account-delete"))
                .isEqualTo(AppleSignInNotificationEvent.Type.ACCOUNT_DELETE);
        assertThat(AppleSignInNotificationEvent.Type.fromValue("email-disabled"))
                .isEqualTo(AppleSignInNotificationEvent.Type.EMAIL_DISABLED);
        assertThat(AppleSignInNotificationEvent.Type.fromValue("email-enabled"))
                .isEqualTo(AppleSignInNotificationEvent.Type.EMAIL_ENABLED);
    }

    @Test
    void mapsUnknownAndNullToUnknown() {
        assertThat(AppleSignInNotificationEvent.Type.fromValue(null))
                .isEqualTo(AppleSignInNotificationEvent.Type.UNKNOWN);
        assertThat(AppleSignInNotificationEvent.Type.fromValue(""))
                .isEqualTo(AppleSignInNotificationEvent.Type.UNKNOWN);
        assertThat(AppleSignInNotificationEvent.Type.fromValue("some-future-apple-event"))
                .isEqualTo(AppleSignInNotificationEvent.Type.UNKNOWN);
    }

    @Test
    void mappingIsCaseInsensitiveAndTrimmed() {
        assertThat(AppleSignInNotificationEvent.Type.fromValue("  Consent-Revoked  "))
                .isEqualTo(AppleSignInNotificationEvent.Type.CONSENT_REVOKED);
    }

    @Test
    void hardEventsRequirePurge() {
        assertThat(eventOf(AppleSignInNotificationEvent.Type.CONSENT_REVOKED).requiresAccountPurge()).isTrue();
        assertThat(eventOf(AppleSignInNotificationEvent.Type.ACCOUNT_DELETE).requiresAccountPurge()).isTrue();
    }

    @Test
    void softEventsDoNotRequirePurge() {
        assertThat(eventOf(AppleSignInNotificationEvent.Type.EMAIL_DISABLED).requiresAccountPurge()).isFalse();
        assertThat(eventOf(AppleSignInNotificationEvent.Type.EMAIL_ENABLED).requiresAccountPurge()).isFalse();
        assertThat(eventOf(AppleSignInNotificationEvent.Type.UNKNOWN).requiresAccountPurge()).isFalse();
    }

    private static AppleSignInNotificationEvent eventOf(AppleSignInNotificationEvent.Type type) {
        return new AppleSignInNotificationEvent(type, "subject", null, Instant.EPOCH);
    }
}
