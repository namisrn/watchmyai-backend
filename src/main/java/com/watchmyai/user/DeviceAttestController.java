package com.watchmyai.user;

import com.watchmyai.config.AppAttestProperties;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Anonymous device onboarding via Apple App Attest. Lets the app obtain a guest session
 * (and therefore use the AI + purchase a subscription) without Sign in with Apple — required
 * by App Review Guideline 5.1.1(v). Both endpoints are public; the attestation itself is the
 * trust gate.
 */
@RestController
@RequestMapping("/api/v1/device")
public class DeviceAttestController {

    private final DeviceAttestChallengeService challengeService;
    private final AppAttestService appAttestService;
    private final AppAttestProperties properties;

    public DeviceAttestController(
            DeviceAttestChallengeService challengeService,
            AppAttestService appAttestService,
            AppAttestProperties properties
    ) {
        this.challengeService = challengeService;
        this.appAttestService = appAttestService;
        this.properties = properties;
    }

    @PostMapping("/attest/challenge")
    public DeviceAttestChallengeResponse challenge() {
        String challenge = challengeService.issueChallenge();
        return new DeviceAttestChallengeResponse(
                challenge,
                Instant.now().plus(properties.challengeTtl())
        );
    }

    @PostMapping("/attest")
    public AuthSessionResponse attest(@Valid @RequestBody DeviceAttestRequest request) {
        return AuthSessionResponse.fromGuest(appAttestService.attest(
                request.keyId(),
                request.attestationObject(),
                request.challenge(),
                request.source(),
                request.deviceName()
        ));
    }
}
