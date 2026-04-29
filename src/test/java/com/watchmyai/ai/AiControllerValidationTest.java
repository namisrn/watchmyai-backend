package com.watchmyai.ai;

import com.watchmyai.common.api.GlobalExceptionHandler;
import com.watchmyai.quota.PlanType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@Import(GlobalExceptionHandler.class)
class AiControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiService aiService;

    @Test
    void askReturnsOkForValidRequest() throws Exception {
        when(aiService.ask(any(AskAIRequest.class)))
                .thenReturn(new AskAIResponse(
                        "Test answer",
                        "gpt-5.4-mini",
                        PlanType.FREE,
                        true,
                        10,
                        10,
                        new BigDecimal("0.001000"),
                        new BigDecimal("0.010000"),
                        "normal"
                ));

        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "Erkläre PostgreSQL kurz.",
                                  "source": "watch",
                                  "mode": "explain",
                                  "language": "de",
                                  "clientRequestId": "test-request-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestAllowed").value(true));
    }

    @Test
    void askReturnsBadRequestForBlankInput() throws Exception {
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "",
                                  "source": "watch",
                                  "mode": "explain",
                                  "language": "de",
                                  "clientRequestId": "test-request-001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("input")));
    }

    @Test
    void askReturnsBadRequestForInvalidSource() throws Exception {
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "Hallo",
                                  "source": "web",
                                  "mode": "explain",
                                  "language": "de",
                                  "clientRequestId": "test-request-001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("source")));
    }
    @Test
    void askReturnsBadRequestForInvalidMode() throws Exception {
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "input": "Hallo",
                              "source": "watch",
                              "mode": "hack",
                              "language": "de",
                              "clientRequestId": "test-request-001"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("mode")));
    }

    @Test
    void askReturnsBadRequestForInvalidLanguage() throws Exception {
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "input": "Hallo",
                              "source": "watch",
                              "mode": "explain",
                              "language": "fr",
                              "clientRequestId": "test-request-001"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("language")));
    }

    @Test
    void askReturnsBadRequestForShortClientRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "input": "Hallo",
                              "source": "watch",
                              "mode": "explain",
                              "language": "de",
                              "clientRequestId": "123"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("clientRequestId")));
    }
    @Test
    void askReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "Hallo",
                                  "source": "watch",
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request."));
    }
}
