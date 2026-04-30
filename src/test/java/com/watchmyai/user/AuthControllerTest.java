package com.watchmyai.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserContextService userContextService;

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
}
