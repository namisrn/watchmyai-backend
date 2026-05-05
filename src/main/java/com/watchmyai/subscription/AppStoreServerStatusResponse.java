package com.watchmyai.subscription;

public record AppStoreServerStatusResponse(
        String bundleId,
        String environment,
        boolean verificationEnabled,
        boolean credentialsConfigured,
        boolean productionReady
) {
}
