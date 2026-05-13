package com.watchmyai.user;

import com.watchmyai.config.SessionProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class AppSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserSessionRepository userSessionRepository;
    private final AppUserService appUserService;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    public AppSessionService(
            UserSessionRepository userSessionRepository,
            AppUserService appUserService,
            SessionProperties sessionProperties,
            Clock clock
    ) {
        this.userSessionRepository = userSessionRepository;
        this.appUserService = appUserService;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
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

        return new CreatedSession(
                sessionToken,
                expiresAt,
                appUser.getUserId(),
                appUser.getAppAccountToken().toString()
        );
    }

    @Transactional(readOnly = true)
    public Optional<UserIdentity> resolveIdentity(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        return userSessionRepository
                .findByTokenHash(hashToken(sessionToken))
                .filter(session -> session.isActive(now))
                .flatMap(session -> appUserService
                        .findByUserId(session.getUserId())
                        .map(user -> new UserIdentity(user.getUserId(), user.getAppAccountToken().toString())));
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
