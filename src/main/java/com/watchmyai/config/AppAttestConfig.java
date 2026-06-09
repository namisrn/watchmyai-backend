package com.watchmyai.config;

import com.webauthn4j.anchor.TrustAnchorRepository;
import com.webauthn4j.appattest.DeviceCheckManager;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Set;

/**
 * Builds a strict {@link DeviceCheckManager} whose attestation certificate chain is validated
 * against Apple's App Attest Root CA (bundled as a classpath resource). The non-strict factory
 * webauthn4j also ships would accept self-signed chains, so it is intentionally not used.
 */
@Configuration
public class AppAttestConfig {

    private static final String APPLE_ROOT_CA = "apple/Apple_App_Attestation_Root_CA.pem";

    @Bean
    DeviceCheckManager deviceCheckManager() {
        Set<TrustAnchor> anchors = Set.of(new TrustAnchor(loadAppleRootCertificate(), null));

        // App Attest credentials carry a fixed AAGUID ("appattest" / "appattestdevelop"); we trust
        // Apple's single root for any AAGUID and let cert-path validation enforce the actual chain.
        TrustAnchorRepository trustAnchorRepository = new TrustAnchorRepository() {
            @Override
            public Set<TrustAnchor> find(AAGUID aaguid) {
                return anchors;
            }

            @Override
            public Set<TrustAnchor> find(byte[] attestationCertificateKeyIdentifier) {
                return anchors;
            }
        };

        DefaultCertPathTrustworthinessVerifier certPathVerifier =
                new DefaultCertPathTrustworthinessVerifier(trustAnchorRepository);
        // Apple App Attest leaf certificates are short-lived and not published to CRL/OCSP.
        certPathVerifier.setRevocationCheckEnabled(false);

        return new DeviceCheckManager(certPathVerifier);
    }

    private X509Certificate loadAppleRootCertificate() {
        try (InputStream in = new ClassPathResource(APPLE_ROOT_CA).getInputStream()) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certificateFactory.generateCertificate(in);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Apple App Attest Root CA certificate.", exception);
        }
    }
}
