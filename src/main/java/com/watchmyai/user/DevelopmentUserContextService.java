package com.watchmyai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.regex.Pattern;

@Service
public class DevelopmentUserContextService implements UserContextService {

    public static final String USER_ID_HEADER = "X-WatchMyAI-User-Id";

    private static final String DEVELOPMENT_USER_ID = "debug-user";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern SAFE_USER_ID = Pattern.compile("[A-Za-z0-9._:-]{3,100}");

    private final ObjectProvider<HttpServletRequest> requestProvider;
    private final AppleIdentityTokenVerifier appleIdentityTokenVerifier;
    private final Environment environment;

    public DevelopmentUserContextService(
            ObjectProvider<HttpServletRequest> requestProvider,
            AppleIdentityTokenVerifier appleIdentityTokenVerifier,
            Environment environment
    ) {
        this.requestProvider = requestProvider;
        this.appleIdentityTokenVerifier = appleIdentityTokenVerifier;
        this.environment = environment;
    }

    @Override
    public UserIdentity getCurrentUser() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            return new UserIdentity(DEVELOPMENT_USER_ID);
        }

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            AppleUserIdentity appleUser = appleIdentityTokenVerifier.verify(
                    authorization.substring(BEARER_PREFIX.length()).trim()
            );

            return new UserIdentity("apple:" + appleUser.subject());
        }

        if (!isDevelopmentProfile()) {
            throw new AuthenticationRequiredException("Authentication is required.");
        }

        String headerUserId = request.getHeader(USER_ID_HEADER);
        if (headerUserId == null || headerUserId.isBlank()) {
            return new UserIdentity(DEVELOPMENT_USER_ID);
        }

        String userId = headerUserId.trim();
        if (!SAFE_USER_ID.matcher(userId).matches()) {
            throw new IllegalArgumentException("Invalid user identity.");
        }

        return new UserIdentity(userId);
    }

    private boolean isDevelopmentProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test");
    }
}
