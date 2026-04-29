package com.watchmyai.user;

import org.springframework.stereotype.Service;

@Service
public class DevelopmentUserContextService implements UserContextService {

    private static final String DEVELOPMENT_USER_ID = "debug-user";

    @Override
    public UserIdentity getCurrentUser() {
        return new UserIdentity(DEVELOPMENT_USER_ID);
    }
}