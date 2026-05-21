package com.watchmyai.user;

import com.watchmyai.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserContextService userContextService;

    @MockitoBean
    private AppleIdentityTokenVerifier appleIdentityTokenVerifier;

    @MockitoBean
    private AppUserService appUserService;

    @MockitoBean
    private AppSessionService appSessionService;

    @Test
    void statusReturnsAppleUser() throws Exception {
        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity("apple:subject-123"));

        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("apple:subject-123"))
                .andExpect(jsonPath("$.userType").value("apple"));
    }

    @Test
    void statusReturnsDevelopmentUser() throws Exception {
        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity("dev-test-user"));

        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("dev-test-user"))
                .andExpect(jsonPath("$.userType").value("development"));
    }

    @Test
    void statusReturnsUnauthorizedWhenSessionIsMissing() throws Exception {
        when(userContextService.getCurrentUser())
                .thenThrow(new AuthenticationRequiredException("Authentication is required."));

        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void appleAuthCreatesBackendSession() throws Exception {
        AppleUserIdentity appleIdentity = new AppleUserIdentity("subject-123", "user@example.com");
        AppUserEntity appUser = new AppUserEntity("subject-123", "apple-user", "user@example.com");
        when(appleIdentityTokenVerifier.verify(eq("identity-token"), any()))
                .thenReturn(appleIdentity);
        when(appUserService.findOrCreateAppleUser(appleIdentity, "apple-user"))
                .thenReturn(appUser);
        when(appSessionService.createSession(eq(appUser), eq("ios"), any()))
                .thenReturn(new AppSessionService.CreatedSession(
                        "session-token",
                        Instant.parse("2026-06-01T00:00:00Z"),
                        "apple:subject-123",
                        "de305d54-75b4-431b-adb2-eb6b9e546014"
                ));

        mockMvc.perform(post("/api/v1/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identityToken": "identity-token",
                                  "authorizationCode": "auth-code",
                                  "appleUserId": "apple-user",
                                  "source": "ios",
                                  "deviceName": "iPhone"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("session-token"))
                .andExpect(jsonPath("$.appAccountToken").value("de305d54-75b4-431b-adb2-eb6b9e546014"));
    }
}
