package com.watchmyai.user;

import com.watchmyai.config.AppAttestProperties;
import com.webauthn4j.appattest.DeviceCheckManager;
import com.webauthn4j.appattest.data.DCAttestationData;
import com.webauthn4j.appattest.data.DCAttestationParameters;
import com.webauthn4j.appattest.data.DCAttestationRequest;
import com.webauthn4j.appattest.server.DCServerProperty;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Verifies an Apple App Attest attestation and, on success, mints a guest session.
 *
 * <p>Client/server contract (must stay in lock-step with the iOS/watchOS {@code GuestIdentity}):
 * the app requests a one-time {@code challenge} string, computes
 * {@code clientDataHash = SHA-256(UTF-8(challenge))}, calls
 * {@code DCAppAttestService.attestKey(keyId, clientDataHash)} and posts {@code keyId} +
 * {@code attestationObject} + {@code challenge} here. webauthn4j then verifies
 * {@code nonce == SHA-256(authenticatorData ‖ clientDataHash)} and
 * {@code keyId == SHA-256(publicKey)}, which binds the attestation to our fresh challenge — so a
 * replayed/old attestation cannot validate against a newly issued challenge.</p>
 */
@Service
public class AppAttestService {

    private static final Logger log = LoggerFactory.getLogger(AppAttestService.class);
    private static final String GUEST_PREFIX = "guest:";

    private final DeviceCheckManager deviceCheckManager;
    private final AppAttestProperties properties;
    private final DeviceAttestChallengeService challengeService;
    private final DeviceAttestationRepository deviceAttestationRepository;
    private final AppSessionService appSessionService;

    public AppAttestService(
            DeviceCheckManager deviceCheckManager,
            AppAttestProperties properties,
            DeviceAttestChallengeService challengeService,
            DeviceAttestationRepository deviceAttestationRepository,
            AppSessionService appSessionService
    ) {
        this.deviceCheckManager = deviceCheckManager;
        this.properties = properties;
        this.challengeService = challengeService;
        this.deviceAttestationRepository = deviceAttestationRepository;
        this.appSessionService = appSessionService;
    }

    @Transactional
    public AppSessionService.CreatedSession attest(
            String keyIdBase64,
            String attestationObjectBase64,
            String challenge,
            String source,
            String deviceName
    ) {
        if (properties.teamId().isBlank() || properties.bundleId().isBlank()) {
            throw new AppAttestException("App Attest is not configured on the server.");
        }
        if (!challengeService.consumeChallenge(challenge)) {
            throw new AppAttestException("Attestation challenge is invalid or expired.");
        }

        byte[] keyId;
        byte[] attestationObject;
        try {
            keyId = Base64.getDecoder().decode(keyIdBase64);
            attestationObject = Base64.getDecoder().decode(attestationObjectBase64);
        } catch (IllegalArgumentException exception) {
            throw new AppAttestException("Malformed attestation payload.", exception);
        }

        byte[] challengeBytes = challenge.getBytes(StandardCharsets.UTF_8);
        byte[] clientDataHash = sha256(challengeBytes);

        DCServerProperty serverProperty =
                new DCServerProperty(properties.teamId(), properties.bundleId(), new DefaultChallenge(challengeBytes));
        DCAttestationRequest attestationRequest =
                new DCAttestationRequest(keyId, attestationObject, clientDataHash);
        DCAttestationParameters attestationParameters = new DCAttestationParameters(serverProperty);

        DCAttestationData attestationData;
        try {
            attestationData = deviceCheckManager.validate(attestationRequest, attestationParameters);
        } catch (RuntimeException exception) {
            log.warn("App Attest validation failed: {}", exception.getMessage());
            throw new AppAttestException("App Attest validation failed.", exception);
        }

        // signCount is only needed for assertion replay-detection (a later hardening step); the
        // one-time attestation + UNIQUE key_id already make the guest identity genuine and unique.
        long signCount = 0L;
        String guestUserId = GUEST_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(keyId));

        deviceAttestationRepository.findByKeyId(keyIdBase64).ifPresentOrElse(
                existing -> {
                    existing.setSignCount(signCount);
                    deviceAttestationRepository.save(existing);
                },
                () -> deviceAttestationRepository.save(
                        new DeviceAttestationEntity(keyIdBase64, guestUserId, attestationObject, signCount))
        );

        log.info("App Attest verified; issuing guest session userId={} source={}", guestUserId, source);
        return appSessionService.createGuestSession(guestUserId, source, deviceName);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
