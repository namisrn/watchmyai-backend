package com.watchmyai.subscription;

import com.watchmyai.config.AppStoreServerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppStoreServerServiceTest {

    @Test
    void statusReportsProductionReadinessFromProperties() {
        AppStoreServerService service = new AppStoreServerService(new AppStoreServerProperties(
                "com.sasanrafatnami.WatchMyAI",
                123456789L,
                "issuer",
                "key",
                "private-key",
                "SANDBOX",
                false
        ));

        AppStoreServerStatusResponse status = service.status();

        assertThat(status.credentialsConfigured()).isTrue();
        assertThat(status.productionReady()).isFalse();
    }

    @Test
    void rejectsMalformedSignedPayload() {
        AppStoreServerService service = new AppStoreServerService(new AppStoreServerProperties(
                "com.sasanrafatnami.WatchMyAI",
                0L,
                "",
                "",
                "",
                "SANDBOX",
                false
        ));

        assertThatThrownBy(() -> service.verifyClientTransactionPayload("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid App Store signed payload.");
    }
}
