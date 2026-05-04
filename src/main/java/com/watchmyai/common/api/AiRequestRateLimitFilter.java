package com.watchmyai.common.api;

import com.watchmyai.user.DevelopmentUserContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
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
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AiRequestRateLimitFilter() {
        this.clock = Clock.systemUTC();
    }

    @Override
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
        long windowStart = Instant.now(clock).getEpochSecond() / WINDOW_SECONDS;
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new WindowCounter(windowStart);
            }
            return existing;
        });

        return counter.count.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW;
    }

    private String resolveKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return "bearer:" + stableHash(authorization);
        }

        String developmentUser = request.getHeader(DevelopmentUserContextService.USER_ID_HEADER);
        if (developmentUser != null && !developmentUser.isBlank()) {
            return "dev:" + developmentUser.trim();
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
