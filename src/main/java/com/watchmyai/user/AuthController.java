package com.watchmyai.user;

import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserContextService userContextService;
    private final AppleIdentityTokenVerifier appleIdentityTokenVerifier;
    private final AppUserService appUserService;
    private final AppSessionService appSessionService;

    public AuthController(
            UserContextService userContextService,
            AppleIdentityTokenVerifier appleIdentityTokenVerifier,
            AppUserService appUserService,
            AppSessionService appSessionService
    ) {
        this.userContextService = userContextService;
        this.appleIdentityTokenVerifier = appleIdentityTokenVerifier;
        this.appUserService = appUserService;
        this.appSessionService = appSessionService;
    }

    @GetMapping("/status")
    public AuthStatusResponse status() {
        return AuthStatusResponse.from(userContextService.getCurrentUser());
    }

    @PostMapping("/apple")
    public AuthSessionResponse apple(@Valid @RequestBody AppleAuthRequest request) {
        AppleUserIdentity appleUser = appleIdentityTokenVerifier.verify(request.identityToken());
        AppUserEntity appUser = appUserService.findOrCreateAppleUser(appleUser, request.appleUserId());

        return AuthSessionResponse.from(appSessionService.createSession(
                appUser,
                request.source(),
                request.deviceName()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest logoutRequest,
            HttpServletRequest httpRequest
    ) {
        appSessionService.revoke(resolveSessionToken(logoutRequest, httpRequest));
        return ResponseEntity.noContent().build();
    }

    private String resolveSessionToken(LogoutRequest logoutRequest, HttpServletRequest request) {
        if (logoutRequest != null && logoutRequest.sessionToken() != null && !logoutRequest.sessionToken().isBlank()) {
            return logoutRequest.sessionToken();
        }

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }

        throw new AuthenticationRequiredException("Authentication is required.");
    }
}
