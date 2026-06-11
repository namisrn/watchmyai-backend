package com.watchmyai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Production user resolution. Identity is derived ONLY from a verified bearer session token;
 * there is deliberately no {@code X-WatchMyAI-User-Id} header fallback, so no request header can
 * impersonate a user on a real deployment — even if one is sent.
 *
 * <p>Active for every profile except {@code dev}/{@code test} (including the default no-profile
 * case). That makes the secure, header-less resolver the fail-safe default: an instance launched
 * without an explicit profile authenticates strictly by session token rather than silently
 * trusting a header, which {@link DevelopmentUserContextService} only does under dev/test.
 */
@Service
@Profile("!dev & !test")
public class ProductionUserContextService implements UserContextService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectProvider<HttpServletRequest> requestProvider;
    private final AppSessionService appSessionService;

    public ProductionUserContextService(
            ObjectProvider<HttpServletRequest> requestProvider,
            AppSessionService appSessionService
    ) {
        this.requestProvider = requestProvider;
        this.appSessionService = appSessionService;
    }

    @Override
    public UserIdentity getCurrentUser() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            throw new AuthenticationRequiredException("Authentication is required.");
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationRequiredException("Authentication is required.");
        }

        return appSessionService
                .resolveIdentity(authorization.substring(BEARER_PREFIX.length()).trim())
                .orElseThrow(() -> new AuthenticationRequiredException("Session is invalid or expired."));
    }
}
