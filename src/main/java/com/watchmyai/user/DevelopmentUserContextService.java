package com.watchmyai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class DevelopmentUserContextService implements UserContextService {

    public static final String USER_ID_HEADER = "X-WatchMyAI-User-Id";

    private static final String DEVELOPMENT_USER_ID = "debug-user";
    private static final Pattern SAFE_USER_ID = Pattern.compile("[A-Za-z0-9._:-]{3,100}");

    private final ObjectProvider<HttpServletRequest> requestProvider;

    public DevelopmentUserContextService(ObjectProvider<HttpServletRequest> requestProvider) {
        this.requestProvider = requestProvider;
    }

    @Override
    public UserIdentity getCurrentUser() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            return new UserIdentity(DEVELOPMENT_USER_ID);
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
}
