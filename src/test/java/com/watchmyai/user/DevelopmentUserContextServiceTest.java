package com.watchmyai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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
        DevelopmentUserContextService service = new DevelopmentUserContextService(requestProvider);

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
        DevelopmentUserContextService service = new DevelopmentUserContextService(requestProvider);

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
        DevelopmentUserContextService service = new DevelopmentUserContextService(requestProvider);

        assertThatThrownBy(service::getCurrentUser)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid user identity.");
    }
}
