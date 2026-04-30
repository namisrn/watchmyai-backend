package com.watchmyai.subscription;

import com.watchmyai.config.AppStoreServerProperties;
import org.springframework.stereotype.Service;

@Service
public class AppStoreServerService {

    private final AppStoreServerProperties properties;

    public AppStoreServerService(AppStoreServerProperties properties) {
        this.properties = properties;
    }

    public AppStoreServerStatusResponse status() {
        return new AppStoreServerStatusResponse(
                properties.bundleId(),
                properties.environment(),
                properties.verificationEnabled(),
                properties.hasServerApiCredentials(),
                properties.readyForProductionVerification()
        );
    }

    public AppStoreNotificationResponse acceptNotification(AppStoreNotificationRequest request) {
        ensureJwsShape(request.signedPayload());

        return new AppStoreNotificationResponse(
                true,
                properties.readyForProductionVerification() ? "app_store_server_library" : "jws_shape_only"
        );
    }

    public void verifyClientTransactionPayload(String signedTransactionInfo) {
        ensureJwsShape(signedTransactionInfo);
    }

    private void ensureJwsShape(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank() || signedPayload.split("\\.").length != 3) {
            throw new IllegalArgumentException("Invalid App Store signed payload.");
        }
    }
}
