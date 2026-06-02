package com.watchmyai.common.api;

import com.watchmyai.user.AuthenticationRequiredException;
import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiAuthenticationFilterTest {

    @Test
    void publicPlanCatalogPassesWithoutUserContextLookup() throws Exception {
        UserContextService userContextService = mock(UserContextService.class);
        ApiAuthenticationFilter filter = new ApiAuthenticationFilter(provider(userContextService));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/plans");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verify(userContextService, never()).getCurrentUser();
    }

    @Test
    void protectedRouteRejectsMissingSession() throws Exception {
        UserContextService userContextService = mock(UserContextService.class);
        when(userContextService.getCurrentUser())
                .thenThrow(new AuthenticationRequiredException("Authentication is required."));
        ApiAuthenticationFilter filter = new ApiAuthenticationFilter(provider(userContextService));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/quota/status");
        request.setAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE, "request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Authentication is required.");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void protectedRoutePassesAuthenticatedSession() throws Exception {
        UserContextService userContextService = mock(UserContextService.class);
        when(userContextService.getCurrentUser()).thenReturn(new UserIdentity("apple:subject"));
        ApiAuthenticationFilter filter = new ApiAuthenticationFilter(provider(userContextService));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/ask");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private ObjectProvider<UserContextService> provider(UserContextService userContextService) {
        @SuppressWarnings("unchecked")
        ObjectProvider<UserContextService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(userContextService);
        return provider;
    }
}
