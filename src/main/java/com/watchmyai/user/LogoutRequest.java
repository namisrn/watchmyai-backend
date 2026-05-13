package com.watchmyai.user;

import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Size(max = 255)
        String sessionToken
) {
}
