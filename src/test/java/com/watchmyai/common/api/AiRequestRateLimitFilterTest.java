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

class AiRequestRateLimitFilterTest {

    @Test
    void usesInMemoryLimitInTestProfile() throws Exception {
        AiRequestRateLimitFilter filter = newFilter(null, "test");

        for (int index = 0; index < 30; index++) {
            MockHttpServletResponse response = doAiAsk(filter, "198.51.100.10");

            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = doAiAsk(filter, "198.51.100.10");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Too many AI requests");
    }

    @Test
    void usesRedisLimitOutsideDevelopmentProfiles() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.increment(anyString())).thenReturn(1L, 31L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        AiRequestRateLimitFilter filter = newFilter(redisTemplate, "prod");

        MockHttpServletResponse allowed = doAiAsk(filter, "198.51.100.20");
        MockHttpServletResponse blocked = doAiAsk(filter, "198.51.100.20");

        assertThat(allowed.getStatus()).isEqualTo(200);
        assertThat(blocked.getStatus()).isEqualTo(429);
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    void rejectsMissingRedisInProduction() {
        AiRequestRateLimitFilter filter = newFilter(null, "prod");

        assertThatThrownBy(() -> doAiAsk(filter, "198.51.100.30"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis rate limiter is required");
    }

    private MockHttpServletResponse doAiAsk(
            AiRequestRateLimitFilter filter,
            String remoteAddress
    ) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/ask");
        request.setRemoteAddr(remoteAddress);
        request.setAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE, "test-request-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private AiRequestRateLimitFilter newFilter(
            StringRedisTemplate redisTemplate,
            String activeProfile
    ) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);

        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{activeProfile});

        return new AiRequestRateLimitFilter(provider, environment);
    }
}
