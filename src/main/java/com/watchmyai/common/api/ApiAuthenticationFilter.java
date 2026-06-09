package com.watchmyai.common.api;

import com.watchmyai.user.AuthenticationRequiredException;
import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Central auth gate for API routes. Controllers still resolve the current user through
 * {@link UserContextService}, but this filter also feeds Spring Security's authorization
 * step so newly-added API routes cannot accidentally become public.
 */
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private record PublicEndpoint(String method, String path, boolean prefixMatch) {
    }

    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
            new PublicEndpoint("GET", "/api/v1/plans", false),
            new PublicEndpoint("POST", "/api/v1/device/attest/challenge", false),
            new PublicEndpoint("POST", "/api/v1/device/attest", false),
            new PublicEndpoint("POST", "/api/v1/auth/apple", false),
            new PublicEndpoint("POST", "/api/v1/auth/logout", false),
            new PublicEndpoint("POST", "/api/v1/auth/apple/notifications", false),
            new PublicEndpoint("POST", "/api/v1/app-store/notifications", false),
            new PublicEndpoint("POST", "/api/v1/telemetry/events", false)
    );

    private final ObjectProvider<UserContextService> userContextServiceProvider;

    public ApiAuthenticationFilter(ObjectProvider<UserContextService> userContextServiceProvider) {
        this.userContextServiceProvider = userContextServiceProvider;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresApiAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UserContextService userContextService = userContextServiceProvider.getIfAvailable();
            if (userContextService == null) {
                writeUnauthorizedResponse(request, response, "Authentication is required.");
                return;
            }

            UserIdentity identity = userContextService.getCurrentUser();
            var authentication = new PreAuthenticatedAuthenticationToken(
                    identity,
                    null,
                    AuthorityUtils.NO_AUTHORITIES
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationRequiredException exception) {
            writeUnauthorizedResponse(request, response, exception.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresApiAuthentication(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return false;
        }
        return PUBLIC_ENDPOINTS.stream().noneMatch(endpoint -> matches(endpoint, request));
    }

    private boolean matches(PublicEndpoint endpoint, HttpServletRequest request) {
        if (!endpoint.method().equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return endpoint.prefixMatch()
                ? path.startsWith(endpoint.path())
                : endpoint.path().equals(path);
    }

    private void writeUnauthorizedResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            String message
    ) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Object requestId = request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE);
        String safeMessage = message == null || message.isBlank()
                ? "Authentication is required."
                : message;
        response.getWriter().write("""
                {"timestamp":"%s","status":401,"error":"Unauthorized","message":"%s","path":"%s","clientRequestId":null,"requestId":"%s","fieldErrors":[]}
                """.formatted(
                Instant.now(),
                jsonEscape(safeMessage),
                jsonEscape(request.getRequestURI()),
                requestId == null ? "" : jsonEscape(requestId.toString())
        ));
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
