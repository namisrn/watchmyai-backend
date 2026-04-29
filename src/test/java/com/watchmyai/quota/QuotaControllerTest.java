package com.watchmyai.quota;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuotaController.class)
class QuotaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuotaService quotaService;

    @MockitoBean
    private DebugPlanService debugPlanService;

    @Test
    void statusReturnsCurrentQuotaWithoutInternalLimits() throws Exception {
        PlanLimits limits = new PlanLimits(
                PlanType.PLUS,
                0,
                1000,
                0,
                300,
                2.00
        );
        QuotaCheckResult quota = new QuotaCheckResult(
                PlanType.PLUS,
                true,
                734,
                0,
                0,
                26,
                0.52,
                2.00,
                "normal",
                limits
        );

        when(debugPlanService.getCurrentPlan())
                .thenReturn(PlanType.PLUS);
        when(quotaService.checkQuota(PlanType.PLUS))
                .thenReturn(quota);

        mockMvc.perform(get("/api/v1/quota/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PLUS"))
                .andExpect(jsonPath("$.requestAllowed").value(true))
                .andExpect(jsonPath("$.remainingRequests").value(734))
                .andExpect(jsonPath("$.monthlyUsagePercent").value(26))
                .andExpect(jsonPath("$.estimatedMonthlyCostEur").value(0.52))
                .andExpect(jsonPath("$.monthlyCostCapEur").value(2.00))
                .andExpect(jsonPath("$.throttleState").value("normal"))
                .andExpect(jsonPath("$.limits").doesNotExist());
    }
}
