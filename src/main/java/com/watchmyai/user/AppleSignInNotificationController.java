package com.watchmyai.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Apple Sign-In server-to-server notifications. The URL of this
 * endpoint must be configured in the Apple Developer Console under
 * <em>Certificates, IDs & Profiles → Identifiers → (App ID) → Sign In with
 * Apple → Server-to-Server Notifications</em>.
 *
 * <p>Apple POSTs a JSON body of the shape {@code { "payload": "<JWS>" }} where
 * the JWS is signed with one of Apple's published JWKS keys. We verify the
 * signature and dispatch the event to {@link AppleSignInNotificationService}.
 *
 * <p>Critical contract with Apple: this endpoint MUST return 2xx for any verified
 * event, otherwise Apple retries (and ultimately gives up). For verification
 * failures we return 400 — Apple's docs say 4xx is treated as "stop retrying";
 * a 5xx would put us in their retry queue, which is OK for transient infra
 * failures but bad for unverifiable payloads.
 */
@RestController
@RequestMapping("/api/v1/auth/apple")
public class AppleSignInNotificationController {

    private static final Logger log = LoggerFactory.getLogger(AppleSignInNotificationController.class);

    private final AppleSignInNotificationVerifier verifier;
    private final AppleSignInNotificationService service;

    public AppleSignInNotificationController(
            AppleSignInNotificationVerifier verifier,
            AppleSignInNotificationService service
    ) {
        this.verifier = verifier;
        this.service = service;
    }

    @PostMapping("/notifications")
    public ResponseEntity<Void> receive(@RequestBody AppleSignInNotificationRequest request) {
        if (request == null || request.payload() == null || request.payload().isBlank()) {
            log.warn("Apple Sign-In notification received without payload");
            return ResponseEntity.badRequest().build();
        }

        AppleSignInNotificationEvent event;
        try {
            event = verifier.verify(request.payload());
        } catch (AppleSignInNotificationException exception) {
            log.warn("Apple Sign-In notification rejected: {}", exception.getMessage());
            return ResponseEntity.badRequest().build();
        }

        try {
            service.handle(event);
        } catch (RuntimeException exception) {
            // Persistence errors etc. — Apple will retry the same notification.
            // Surface as 5xx so Apple retries; the unique notification JTI guards
            // against duplicate processing if we ever add idempotency keys.
            log.error("Apple Sign-In notification handling failed type={} sub={} error={}",
                    event.type(), event.subject(), exception.getMessage(), exception);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok().build();
    }
}
