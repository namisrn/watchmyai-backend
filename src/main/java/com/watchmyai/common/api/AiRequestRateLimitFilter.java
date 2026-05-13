package com.watchmyai.common.api;

import com.watchmyai.user.DevelopmentUserContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AiRequestRateLimitFilter extends OncePerRequestFilter {

    private static final String AI_ASK_PATH = "/api/v1/ai/ask";
    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_REQUESTS_PER_WINDOW = 30;

    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AiRequestRateLimitFilter(ObjectProvider<StringRedisTemplate> redisTemplateProvider, Environment environment) {
        this.clock = Clock.systemUTC();
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.environment = environment;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isProtectedAiRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!allowRequest(resolveKey(request))) {
            writeRateLimitResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedAiRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && AI_ASK_PATH.equals(request.getRequestURI());
    }

    private boolean allowRequest(String key) {
        if (redisTemplate == null) {
            if (isProductionProfile()) {
                throw new IllegalStateException("Redis rate limiter is required in prod.");
            }
            return allowRequestInMemory(key);
        }

        if (isDevelopmentProfile()) {
            return allowRequestInMemory(key);
        }

        try {
            String redisKey = redisKey(key);
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(WINDOW_SECONDS + 5));
            }
            return count != null && count <= MAX_REQUESTS_PER_WINDOW;
        } catch (DataAccessException exception) {
            if (isDevelopmentProfile()) {
                return allowRequestInMemory(key);
            }
            throw exception;
        }
    }

    private boolean allowRequestInMemory(String key) {
        long windowStart = Instant.now(clock).getEpochSecond() / WINDOW_SECONDS;
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new WindowCounter(windowStart);
            }
            return existing;
        });

        return counter.count.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW;
    }

    private boolean isDevelopmentProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test");
    }

    private boolean isProductionProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private String redisKey(String key) {
        long windowStart = Instant.now(clock).getEpochSecond() / WINDOW_SECONDS;
        return "watchmyai:rate-limit:ai-ask:" + stableHash(key) + ":" + windowStart;
    }

    private String resolveKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return "bearer:" + stableHash(authorization);
        }

        if (isDevelopmentProfile()) {
            String developmentUser = request.getHeader(DevelopmentUserContextService.USER_ID_HEADER);
            if (developmentUser != null && !developmentUser.isBlank()) {
                return "dev:" + developmentUser.trim();
            }
        }

        return "ip:" + request.getRemoteAddr();
    }

    private String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String requestId = String.valueOf(request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE));
        response.getWriter().write("""
                {"status":429,"error":"Too Many Requests","message":"Too many AI requests. Please try again shortly.","requestId":"%s","fieldErrors":[]}
                """.formatted(requestId));
    }

    private static final class WindowCounter {
        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
