package com.watchmyai.subscription;

import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.watchmyai.config.AppStoreServerProperties;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class AppStoreServerService {

    private final AppStoreServerProperties properties;
    private final SignedDataVerifier signedDataVerifier;

    public AppStoreServerService(AppStoreServerProperties properties) {
        this.properties = properties;
        this.signedDataVerifier = createVerifierIfReady(properties);
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
        verifyNotificationPayload(request.signedPayload());

        return new AppStoreNotificationResponse(
                true,
                signedDataVerifier == null ? "jws_shape_only" : "app_store_server_library"
        );
    }

    public ResponseBodyV2DecodedPayload verifyNotificationPayload(String signedPayload) {
        ensureJwsShape(signedPayload);

        if (signedDataVerifier == null) {
            return null;
        }

        ResponseBodyV2DecodedPayload payload = verifyNotification(signedPayload);
        Data data = payload.getData();
        if (data != null) {
            verifyNestedData(data);
        }

        return payload;
    }

    public VerificationResult verifyClientTransactionPayload(String signedTransactionInfo) {
        ensureJwsShape(signedTransactionInfo);
        if (signedDataVerifier == null) {
            return VerificationResult.jwsShapeOnly();
        }

        JWSTransactionDecodedPayload payload = verifyTransaction(signedTransactionInfo);
        return VerificationResult.verified(payload);
    }

    private void ensureJwsShape(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank() || signedPayload.split("\\.").length != 3) {
            throw new IllegalArgumentException("Invalid App Store signed payload.");
        }
    }

    private ResponseBodyV2DecodedPayload verifyNotification(String signedPayload) {
        try {
            return signedDataVerifier.verifyAndDecodeNotification(signedPayload);
        } catch (VerificationException ex) {
            throw new IllegalArgumentException("App Store notification verification failed: " + ex.getStatus(), ex);
        }
    }

    private JWSTransactionDecodedPayload verifyTransaction(String signedTransactionInfo) {
        try {
            return signedDataVerifier.verifyAndDecodeTransaction(signedTransactionInfo);
        } catch (VerificationException ex) {
            throw new IllegalArgumentException("App Store transaction verification failed: " + ex.getStatus(), ex);
        }
    }

    private void verifyNestedData(Data data) {
        if (data.getSignedTransactionInfo() != null && !data.getSignedTransactionInfo().isBlank()) {
            verifyTransaction(data.getSignedTransactionInfo());
        }
        if (data.getSignedRenewalInfo() != null && !data.getSignedRenewalInfo().isBlank()) {
            try {
                signedDataVerifier.verifyAndDecodeRenewalInfo(data.getSignedRenewalInfo());
            } catch (VerificationException ex) {
                throw new IllegalArgumentException("App Store renewal verification failed: " + ex.getStatus(), ex);
            }
        }
    }

    private SignedDataVerifier createVerifierIfReady(AppStoreServerProperties properties) {
        if (!properties.readyForProductionVerification()) {
            return null;
        }

        Set<InputStream> roots = loadAppleRootsFromClasspath();
        if (roots.isEmpty()) {
            throw new IllegalStateException(
                    "App Store verification enabled but no Apple root certificates found in classpath resources."
            );
        }

        return new SignedDataVerifier(
                roots,
                properties.bundleId(),
                properties.appAppleId(),
                toEnvironment(properties.environment()),
                true
        );
    }

    private Environment toEnvironment(String raw) {
        if (raw == null || raw.isBlank()) {
            return Environment.SANDBOX;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PRODUCTION" -> Environment.PRODUCTION;
            case "XCODE" -> Environment.XCODE;
            case "LOCAL_TESTING", "LOCALTESTING" -> Environment.LOCAL_TESTING;
            case "SANDBOX" -> Environment.SANDBOX;
            default -> Optional.ofNullable(Environment.fromValue(raw.trim()))
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported App Store environment: " + raw));
        };
    }

    private Set<InputStream> loadAppleRootsFromClasspath() {
        try {
            return Set.of(
                    new ClassPathResource("apple/AppleRootCA-G3.cer").getInputStream(),
                    new ClassPathResource("apple/AppleRootCA-G2.cer").getInputStream()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load Apple root certificates from classpath resources.", ex);
        }
    }

    public record VerificationResult(
            boolean verified,
            String verificationSource,
            JWSTransactionDecodedPayload payload
    ) {
        static VerificationResult jwsShapeOnly() {
            return new VerificationResult(false, "jws_shape_only", null);
        }

        static VerificationResult verified(JWSTransactionDecodedPayload payload) {
            return new VerificationResult(true, "app_store_server_library", payload);
        }
    }
}
