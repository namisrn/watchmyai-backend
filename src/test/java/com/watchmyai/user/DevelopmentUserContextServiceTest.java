package com.watchmyai.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentUserContextServiceTest {

    @Test
    void getCurrentUserReturnsDevelopmentUser() {
        DevelopmentUserContextService service = new DevelopmentUserContextService();

        UserIdentity currentUser = service.getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo("debug-user");
    }
}