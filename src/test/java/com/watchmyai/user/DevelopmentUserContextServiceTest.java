package com.watchmyai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

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
    void getCurrentUserAcceptsRequestHeaderUserWhenDevIsDefaultProfile() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(DevelopmentUserContextService.USER_ID_HEADER))
                .thenReturn("user-123");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        DevelopmentUserContextService service = newService(requestProvider, new String[]{}, new String[]{"dev"});

        UserIdentity currentUser = service.getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo("user-123");
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
    void getCurrentUserReturnsAppleSubjectForBearerToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer apple-token");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        AppleIdentityTokenVerifier tokenVerifier = mock(AppleIdentityTokenVerifier.class);
        when(tokenVerifier.verify("apple-token"))
                .thenReturn(new AppleUserIdentity("apple-subject", "user@example.com"));
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles())
                .thenReturn(new String[]{"prod"});
        when(environment.getDefaultProfiles())
                .thenReturn(new String[]{"default"});
        DevelopmentUserContextService service = new DevelopmentUserContextService(
                requestProvider,
                tokenVerifier,
                environment
        );

        UserIdentity currentUser = service.getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo("apple:apple-subject");
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
        AppleIdentityTokenVerifier tokenVerifier = mock(AppleIdentityTokenVerifier.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles())
                .thenReturn(activeProfiles);
        when(environment.getDefaultProfiles())
                .thenReturn(defaultProfiles);

        return new DevelopmentUserContextService(requestProvider, tokenVerifier, environment);
    }
}
