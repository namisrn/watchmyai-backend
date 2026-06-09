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
    private final AccountDeletionService accountDeletionService;
    private final GuestMigrationService guestMigrationService;

    public AuthController(
            UserContextService userContextService,
            AppleIdentityTokenVerifier appleIdentityTokenVerifier,
            AppUserService appUserService,
            AppSessionService appSessionService,
            AccountDeletionService accountDeletionService,
            GuestMigrationService guestMigrationService
    ) {
        this.userContextService = userContextService;
        this.appleIdentityTokenVerifier = appleIdentityTokenVerifier;
        this.appUserService = appUserService;
        this.appSessionService = appSessionService;
        this.accountDeletionService = accountDeletionService;
        this.guestMigrationService = guestMigrationService;
    }

    @GetMapping("/status")
    public AuthStatusResponse status() {
        return AuthStatusResponse.from(userContextService.getCurrentUser());
    }

    @PostMapping("/apple")
    public AuthSessionResponse apple(@Valid @RequestBody AppleAuthRequest request) {
        AppleUserIdentity appleUser = appleIdentityTokenVerifier.verify(request.identityToken(), request.nonce());
        AppUserEntity appUser = appUserService.findOrCreateAppleUser(appleUser, request.appleUserId());

        // If this device was using a guest session before signing in, migrate everything it
        // accumulated (usage, subscription, history) onto the account, then let the new account
        // session supersede the guest one. resolveIdentity validates the guest token first.
        String guestSessionToken = request.guestSessionToken();
        if (guestSessionToken != null && !guestSessionToken.isBlank()) {
            appSessionService.resolveIdentity(guestSessionToken)
                    .map(UserIdentity::userId)
                    .ifPresent(guestUserId ->
                            guestMigrationService.migrateGuestToAccount(guestUserId, appUser.getUserId()));
        }

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

    @PostMapping("/delete-account")
    public AccountDeletionResponse deleteAccount(@Valid @RequestBody AccountDeletionRequest request) {
        UserIdentity currentUser = userContextService.getCurrentUser();
        AppleUserIdentity confirmedUser = appleIdentityTokenVerifier.verify(request.identityToken(), request.nonce());
        if (!currentUser.userId().equals("apple:" + confirmedUser.subject())) {
            throw new AuthenticationRequiredException("Apple confirmation does not match the signed-in account.");
        }

        accountDeletionService.deleteAccount(currentUser.userId(), request.authorizationCode());
        return new AccountDeletionResponse(true);
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
