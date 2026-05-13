package com.watchmyai.user;

public record UserIdentity(
        String userId,
        String appAccountToken
) {
    public UserIdentity(String userId) {
        this(userId, null);
    }
}
