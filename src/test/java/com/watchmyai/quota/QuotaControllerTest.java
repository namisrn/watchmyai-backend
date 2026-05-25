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
                60,
                500,
                0,
                300,
                new BigDecimal("1.200000")
        );
        QuotaCheckResult quota = new QuotaCheckResult(
                PlanType.PLUS,
                true,
                367,
                44,
                60,
                27,
                367,
                500,
                0,
                0,
                27,
                new BigDecimal("0.310000"),
                new BigDecimal("1.200000"),
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
                .andExpect(jsonPath("$.remainingRequests").value(367))
                .andExpect(jsonPath("$.dailyRemainingRequests").value(44))
                .andExpect(jsonPath("$.dailyRequestLimit").value(60))
                .andExpect(jsonPath("$.dailyUsagePercent").value(27))
                .andExpect(jsonPath("$.monthlyRemainingRequests").value(367))
                .andExpect(jsonPath("$.monthlyRequestLimit").value(500))
                .andExpect(jsonPath("$.monthlyUsagePercent").value(27))
                .andExpect(jsonPath("$.estimatedMonthlyCostEur").value(0.31))
                .andExpect(jsonPath("$.monthlyCostCapEur").value(1.20))
                .andExpect(jsonPath("$.throttleState").value("normal"))
                .andExpect(jsonPath("$.limits").doesNotExist());

        // Lazy subscription refresh must run before the cached plan is read, otherwise
        // a stale PLUS row in `user_plans` (e.g. after an Apple-S2S notification was
        // lost) would let the user keep PLUS limits even though their entitlement has
        // already expired. Keep the contract identical to `/api/v1/status`.
        verify(subscriptionStatusService).getCurrentStatus();
    }
}
