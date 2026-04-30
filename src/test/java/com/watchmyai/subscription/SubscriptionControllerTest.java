package com.watchmyai.subscription;

import com.watchmyai.quota.PlanType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionStatusService subscriptionStatusService;

    @MockitoBean
    private SubscriptionSyncService subscriptionSyncService;

    @Test
    void statusReturnsCurrentSubscription() throws Exception {
        when(subscriptionStatusService.getCurrentStatus())
                .thenReturn(new SubscriptionStatusResponse(PlanType.PLUS, "watchmyai.plus.monthly", false));

        mockMvc.perform(get("/api/v1/subscription/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PLUS"))
                .andExpect(jsonPath("$.productId").value("watchmyai.plus.monthly"));
    }

    @Test
    void syncReturnsUpdatedSubscription() throws Exception {
        when(subscriptionSyncService.sync(any()))
                .thenReturn(new SubscriptionStatusResponse(
                        PlanType.PRO,
                        "watchmyai.pro.monthly",
                        false,
                        "client_verified",
                        "transaction-1",
                        "original-1",
                        "sandbox"
                ));

        mockMvc.perform(post("/api/v1/subscription/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "watchmyai.pro.monthly",
                                  "transactionId": "transaction-1",
                                  "originalTransactionId": "original-1",
                                  "environment": "sandbox",
                                  "signedTransactionInfo": "signed-jws"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PRO"))
                .andExpect(jsonPath("$.verificationSource").value("client_verified"));
    }
}
