package com.watchmyai.quota;

import com.watchmyai.subscription.SubscriptionStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
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
    private UserPlanService userPlanService;

    @MockitoBean
    private SubscriptionStatusService subscriptionStatusService;

    @Test
    void statusReturnsCurrentQuotaWithoutInternalLimits() throws Exception {
        PlanLimits limits = new PlanLimits(
                PlanType.PLUS,
                0,
                100,
                1000,
                0,
                300,
                new BigDecimal("2.000000")
        );
        QuotaCheckResult quota = new QuotaCheckResult(
                PlanType.PLUS,
                true,
                734,
                74,
                100,
                26,
                734,
                1000,
                0,
                0,
                26,
                new BigDecimal("0.520000"),
                new BigDecimal("2.000000"),
                QuotaState.NORMAL,
                limits
        );

        when(userPlanService.getCurrentPlan())
                .thenReturn(PlanType.PLUS);
        when(quotaService.checkQuota(PlanType.PLUS))
                .thenReturn(quota);

        mockMvc.perform(get("/api/v1/quota/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PLUS"))
                .andExpect(jsonPath("$.requestAllowed").value(true))
                .andExpect(jsonPath("$.remainingRequests").value(734))
                .andExpect(jsonPath("$.dailyRemainingRequests").value(74))
                .andExpect(jsonPath("$.dailyRequestLimit").value(100))
                .andExpect(jsonPath("$.dailyUsagePercent").value(26))
                .andExpect(jsonPath("$.monthlyRemainingRequests").value(734))
                .andExpect(jsonPath("$.monthlyRequestLimit").value(1000))
                .andExpect(jsonPath("$.monthlyUsagePercent").value(26))
                .andExpect(jsonPath("$.estimatedMonthlyCostEur").value(0.52))
                .andExpect(jsonPath("$.monthlyCostCapEur").value(2.00))
                .andExpect(jsonPath("$.throttleState").value("normal"))
                .andExpect(jsonPath("$.limits").doesNotExist());

        // Lazy subscription refresh must run before the cached plan is read, otherwise
        // a stale PLUS row in `user_plans` (e.g. after an Apple-S2S notification was
        // lost) would let the user keep PLUS limits even though their entitlement has
        // already expired. Keep the contract identical to `/api/v1/status`.
        verify(subscriptionStatusService).getCurrentStatus();
    }
}
