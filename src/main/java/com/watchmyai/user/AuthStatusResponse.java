package com.watchmyai.user;

public record AuthStatusResponse(
        boolean authenticated,
        String userId,
        String userType,
        String appAccountToken
) {
    public static AuthStatusResponse from(UserIdentity identity) {
        String userId = identity.userId();
        String userType = userId.startsWith("apple:") ? "apple" : "development";

        return new AuthStatusResponse(true, userId, userType, identity.appAccountToken());
    }
}
