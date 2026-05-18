package com.watchmyai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class DevelopmentUserContextServiceTest {

    @Test
    void getCurrentUserReturnsDevelopmentUser() {
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(null);
        DevelopmentUserContextService service = newService(requestProvider, "dev");

        UserIdentity currentUser = service.getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo("debug-user");
    }

    @Test
    void getCurrentUserReturnsRequestHeaderUser() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(DevelopmentUserContextService.USER_ID_HEADER))
                .thenReturn("user-123");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        DevelopmentUserContextService service = newService(requestProvider, "dev");

        UserIdentity currentUser = service.getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo("user-123");
    }

    @Test
    void getCurrentUserRejectsRequestHeaderUserWhenOnlyDevIsDefaultProfile() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(DevelopmentUserContextService.USER_ID_HEADER))
                .thenReturn("user-123");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        DevelopmentUserContextService service = newService(requestProvider, new String[]{}, new String[]{"dev"});

        assertThatThrownBy(service::getCurrentUser)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void getCurrentUserRejectsUnsafeRequestHeaderUser() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(DevelopmentUserContextService.USER_ID_HEADER))
                .thenReturn("../bad");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        DevelopmentUserContextService service = newService(requestProvider, "dev");

        assertThatThrownBy(service::getCurrentUser)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid user identity.");
    }

    @Test
    void getCurrentUserRejectsMissingAuthenticationOutsideDevelopment() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        DevelopmentUserContextService service = newService(requestProvider, "prod");

        assertThatThrownBy(service::getCurrentUser)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void getCurrentUserRejectsMissingRequestOutsideDevelopment() {
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(null);
        DevelopmentUserContextService service = newService(requestProvider, "prod");

        assertThatThrownBy(service::getCurrentUser)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void getCurrentUserRejectsDevelopmentFallbackWhenProdProfileIsAlsoActive() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        DevelopmentUserContextService service = newService(requestProvider, new String[]{"prod", "dev"}, new String[]{"default"});

        assertThatThrownBy(service::getCurrentUser)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void getCurrentUserReturnsAppleSubjectForBearerToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer session-token");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        AppSessionService appSessionService = mock(AppSessionService.class);
        when(appSessionService.resolveIdentity("session-token"))
                .thenReturn(Optional.of(new UserIdentity(
                        "apple:apple-subject",
                        "de305d54-75b4-431b-adb2-eb6b9e546014"
                )));
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles())
                .thenReturn(new String[]{"prod"});
        when(environment.getDefaultProfiles())
                .thenReturn(new String[]{"default"});
        DevelopmentUserContextService service = new DevelopmentUserContextService(
                requestProvider,
                appSessionService,
                environment
        );

        UserIdentity currentUser = service.getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo("apple:apple-subject");
        assertThat(currentUser.appAccountToken()).isEqualTo("de305d54-75b4-431b-adb2-eb6b9e546014");
    }

    private DevelopmentUserContextService newService(
            ObjectProvider<HttpServletRequest> requestProvider,
            String activeProfile
    ) {
        return newService(requestProvider, new String[]{activeProfile}, new String[]{"default"});
    }

    private DevelopmentUserContextService newService(
            ObjectProvider<HttpServletRequest> requestProvider,
            String[] activeProfiles,
            String[] defaultProfiles
    ) {
        AppSessionService appSessionService = mock(AppSessionService.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles())
                .thenReturn(activeProfiles);
        when(environment.getDefaultProfiles())
                .thenReturn(defaultProfiles);

        return new DevelopmentUserContextService(requestProvider, appSessionService, environment);
    }
}
