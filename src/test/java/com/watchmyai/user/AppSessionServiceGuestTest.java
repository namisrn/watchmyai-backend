package com.watchmyai.user;

import com.watchmyai.config.SessionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guest-session behaviour of {@link AppSessionService}: a guest token must resolve straight from
 * the session row (no {@link AppUserEntity} lookup), and guest sessions reuse the same opaque
 * token + TTL machinery as account sessions.
 */
@ExtendWith(MockitoExtension.class)
class AppSessionServiceGuestTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AppUserService appUserService;

    @SuppressWarnings("unused")
    private final SessionProperties sessionProperties = new SessionProperties(30);

    private AppSessionService newService() {
        return new AppSessionService(userSessionRepository, appUserService, sessionProperties, FIXED_CLOCK);
    }

    @Test
    void createGuestSessionPersistsGuestUserIdAndReturnsToken() {
        AppSessionService service = newService();

        AppSessionService.CreatedSession created =
                service.createGuestSession("guest:abc123", "ios", "iPhone");

        ArgumentCaptor<UserSessionEntity> captor = ArgumentCaptor.forClass(UserSessionEntity.class);
        verify(userSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("guest:abc123");

        assertThat(created.userId()).isEqualTo("guest:abc123");
        assertThat(created.appAccountToken()).isNull();
        assertThat(created.sessionToken()).isNotBlank();
        assertThat(created.expiresAt()).isEqualTo(Instant.parse("2026-07-09T00:00:00Z"));
    }

    @Test
    void resolveIdentityResolvesGuestSessionWithoutAppUserLookup() {
        AppSessionService service = newService();
        UserSessionEntity guestSession = new UserSessionEntity(
                "token-hash", "guest:abc123", "ios", "iPhone",
                Instant.parse("2026-07-09T00:00:00Z")
        );
        when(userSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(guestSession));

        Optional<UserIdentity> identity = service.resolveIdentity("any-token");

        assertThat(identity).isPresent();
        assertThat(identity.get().userId()).isEqualTo("guest:abc123");
        assertThat(identity.get().appAccountToken()).isNull();
        verify(appUserService, never()).findByUserId(anyString());
    }
}
