package com.watchmyai.common.api;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRateLimitFilterTest {

    @Test
    void usesInMemoryLimitInTestProfile() throws Exception {
        ApiRateLimitFilter filter = newFilter(null, "test");

        for (int index = 0; index < 30; index++) {
            MockHttpServletResponse response = doPost(filter, "/api/v1/ai/ask", "198.51.100.10");

            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = doPost(filter, "/api/v1/ai/ask", "198.51.100.10");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Too many requests");
    }

    @Test
    void appliesStricterLimitToAppleAuthEndpoint() throws Exception {
        ApiRateLimitFilter filter = newFilter(null, "test");

        for (int index = 0; index < 10; index++) {
            MockHttpServletResponse response = doPost(filter, "/api/v1/auth/apple", "198.51.100.11");

            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = doPost(filter, "/api/v1/auth/apple", "198.51.100.11");

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void appleAuthAndAiAskUseSeparateBuckets() throws Exception {
        ApiRateLimitFilter filter = newFilter(null, "test");

        for (int index = 0; index < 10; index++) {
            doPost(filter, "/api/v1/auth/apple", "198.51.100.12");
        }

        MockHttpServletResponse aiAsk = doPost(filter, "/api/v1/ai/ask", "198.51.100.12");

        assertThat(aiAsk.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotRateLimitUnlistedEndpoints() throws Exception {
        ApiRateLimitFilter filter = newFilter(null, "test");

        for (int index = 0; index < 50; index++) {
            MockHttpServletResponse response = doPost(filter, "/api/v1/subscription/status", "198.51.100.13");

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void usesRedisLimitOutsideDevelopmentProfiles() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.increment(anyString())).thenReturn(1L, 31L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        ApiRateLimitFilter filter = newFilter(redisTemplate, "prod");

        MockHttpServletResponse allowed = doPost(filter, "/api/v1/ai/ask", "198.51.100.20");
        MockHttpServletResponse blocked = doPost(filter, "/api/v1/ai/ask", "198.51.100.20");

        assertThat(allowed.getStatus()).isEqualTo(200);
        assertThat(blocked.getStatus()).isEqualTo(429);
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    void rejectsMissingRedisInProduction() {
        ApiRateLimitFilter filter = newFilter(null, "prod");

        assertThatThrownBy(() -> doPost(filter, "/api/v1/ai/ask", "198.51.100.30"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis rate limiter is required");
    }

    private MockHttpServletResponse doPost(
            ApiRateLimitFilter filter,
            String path,
            String remoteAddress
    ) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddress);
        request.setAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE, "test-request-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private ApiRateLimitFilter newFilter(
            StringRedisTemplate redisTemplate,
            String activeProfile
    ) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);

        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{activeProfile});

        return new ApiRateLimitFilter(provider, environment);
    }
}
