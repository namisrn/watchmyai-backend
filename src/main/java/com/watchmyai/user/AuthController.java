package com.watchmyai.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserContextService userContextService;

    public AuthController(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    @GetMapping("/status")
    public AuthStatusResponse status() {
        return AuthStatusResponse.from(userContextService.getCurrentUser());
    }
}
