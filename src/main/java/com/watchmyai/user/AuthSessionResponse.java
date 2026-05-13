package com.watchmyai.user;

import java.time.Instant;

public record AuthSessionResponse(
        String sessionToken,
        Instant expiresAt,
        String userId,
        String userType,
        String appAccountToken
) {
    static AuthSessionResponse from(AppSessionService.CreatedSession session) {
        return new AuthSessionResponse(
                session.sessionToken(),
                session.expiresAt(),
                session.userId(),
                "apple",
                session.appAccountToken()
        );
    }
}
