package com.watchmyai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ProductionUserContextServiceTest {

    @Test
    void resolvesUserFromBearerToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer session-token");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        AppSessionService appSessionService = mock(AppSessionService.class);
        when(appSessionService.resolveIdentity("session-token"))
                .thenReturn(Optional.of(new UserIdentity(
                        "apple:apple-subject",
                        "de305d54-75b4-431b-adb2-eb6b9e546014"
                )));

        UserIdentity currentUser = new ProductionUserContextService(requestProvider, appSessionService)
                .getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo("apple:apple-subject");
        assertThat(currentUser.appAccountToken()).isEqualTo("de305d54-75b4-431b-adb2-eb6b9e546014");
    }

    @Test
    void ignoresImpersonationHeaderAndRejectsWhenNoBearerToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(DevelopmentUserContextService.USER_ID_HEADER)).thenReturn("victim-user");
        when(request.getHeader("Authorization")).thenReturn(null);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);

        assertThatThrownBy(() ->
                new ProductionUserContextService(requestProvider, mock(AppSessionService.class)).getCurrentUser())
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void rejectsInvalidOrExpiredSession() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer stale-token");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        AppSessionService appSessionService = mock(AppSessionService.class);
        when(appSessionService.resolveIdentity("stale-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                new ProductionUserContextService(requestProvider, appSessionService).getCurrentUser())
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Session is invalid or expired.");
    }

    @Test
    void rejectsWhenNoRequestInScope() {
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() ->
                new ProductionUserContextService(requestProvider, mock(AppSessionService.class)).getCurrentUser())
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required.");
    }
}
