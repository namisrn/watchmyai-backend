package com.watchmyai.user;

import com.watchmyai.config.SessionProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AppSessionService {

    private static final Logger log = LoggerFactory.getLogger(AppSessionService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration SLIDING_RENEWAL_INTERVAL = Duration.ofDays(1);
    private static final String GUEST_PREFIX = "guest:";

    private final UserSessionRepository userSessionRepository;
    private final AppUserService appUserService;
    private final SessionProperties sessionProperties;
    private final Clock clock;
    private final Counter guestSessionCreatedCounter;

    public AppSessionService(
            UserSessionRepository userSessionRepository,
            AppUserService appUserService,
            SessionProperties sessionProperties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.userSessionRepository = userSessionRepository;
        this.appUserService = appUserService;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
        // Observability for guest-account farming: each App-Attest-backed guest session is a new
        // free-tier identity. A spike here (vs. the App Attest hardware cost that bounds it) is the
        // signal to add per-device throttling. Counter, not gauge — guest sessions are cheap events.
        this.guestSessionCreatedCounter = meterRegistry.counter("watchmyai.guest.session_created");
    }

    @Transactional
    public CreatedSession createSession(AppUserEntity appUser, String source, String deviceName) {
        String sessionToken = generateSessionToken();
        Instant expiresAt = Instant.now(clock).plus(sessionProperties.ttl());
        UserSessionEntity session = new UserSessionEntity(
                hashToken(sessionToken),
                appUser.getUserId(),
                source,
                deviceName,
                expiresAt
        );
        userSessionRepository.save(session);
        log.info("Session created userId={} source={} expiresAt={}", appUser.getUserId(), source, expiresAt);

        return new CreatedSession(
                sessionToken,
                expiresAt,
                appUser.getUserId(),
                appUser.getAppAccountToken().toString()
        );
    }

    /**
     * Guest session for an App-Attest-verified device. Reuses the same opaque-token + hash storage,
     * sliding TTL and revocation as account sessions; the userId is the namespaced
     * {@code guest:<hash(keyId)>} so every per-user table keys off it transparently. No
     * {@link AppUserEntity} exists for guests, hence the null {@code appAccountToken}.
     */
    @Transactional
    public CreatedSession createGuestSession(String guestUserId, String source, String deviceName) {
        String sessionToken = generateSessionToken();
        Instant expiresAt = Instant.now(clock).plus(sessionProperties.ttl());
        UserSessionEntity session = new UserSessionEntity(
                hashToken(sessionToken),
                guestUserId,
                source,
                deviceName,
                expiresAt
        );
        userSessionRepository.save(session);
        guestSessionCreatedCounter.increment();
        log.info("Guest session created userId={} source={} expiresAt={}", guestUserId, source, expiresAt);

        return new CreatedSession(sessionToken, expiresAt, guestUserId, guestAppAccountToken(guestUserId));
    }

    /**
     * Stable App Store {@code appAccountToken} for a guest. Accounts carry one on their
     * {@link AppUserEntity}; guests have no such row, so we derive a deterministic UUID from the
     * (already device-stable) {@code guest:<hash(keyId)>} id. Determinism matters: it survives
     * re-attestation and lets {@code SubscriptionEntitlementService} bind a StoreKit purchase to
     * the guest identity. It is an opaque binding value only — the real entitlement trust is the
     * JWS signature plus the {@code originalTransactionId} ownership check.
     */
    static String guestAppAccountToken(String guestUserId) {
        return UUID.nameUUIDFromBytes(("watchmyai-guest:" + guestUserId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    @Transactional
    public Optional<UserIdentity> resolveIdentity(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        return userSessionRepository
                .findByTokenHash(hashToken(sessionToken))
                .filter(session -> session.isActive(now))
                .flatMap(session -> {
                    // Guest sessions have no AppUser row — resolve them straight from the session,
                    // attaching the deterministic guest appAccountToken so guest StoreKit purchases
                    // bind to the same identity the device used at purchase time.
                    if (session.getUserId().startsWith(GUEST_PREFIX)) {
                        renewIfDue(session, now);
                        return Optional.of(new UserIdentity(
                                session.getUserId(),
                                guestAppAccountToken(session.getUserId())
                        ));
                    }
                    return appUserService
                            .findByUserId(session.getUserId())
                            .map(user -> {
                                renewIfDue(session, now);
                                return new UserIdentity(user.getUserId(), user.getAppAccountToken().toString());
                            });
                });
    }

    /**
     * Sliding-window session renewal: every time an active session is used its expiry is
     * pushed back to the full TTL. The write is throttled to at most once per
     * {@link #SLIDING_RENEWAL_INTERVAL} so it stays cheap on the per-request hot path —
     * only sessions left completely unused for the whole TTL still reach the hard expiry.
     */
    private void renewIfDue(UserSessionEntity session, Instant now) {
        Instant fullExpiry = now.plus(sessionProperties.ttl());
        if (session.getExpiresAt().isBefore(fullExpiry.minus(SLIDING_RENEWAL_INTERVAL))) {
            session.extend(fullExpiry);
        }
    }

    @Transactional
    public void revoke(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return;
        }

        Instant now = Instant.now(clock);
        userSessionRepository
                .findByTokenHash(hashToken(sessionToken))
                .filter(session -> session.isActive(now))
                .ifPresent(session -> session.revoke(now));
    }

    static String hashToken(String sessionToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sessionToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Session token could not be hashed.", exception);
        }
    }

    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record CreatedSession(
            String sessionToken,
            Instant expiresAt,
            String userId,
            String appAccountToken
    ) {
    }
}
