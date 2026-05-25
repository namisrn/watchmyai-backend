package com.watchmyai.quota;

import com.watchmyai.subscription.SubscriptionProductCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanCatalogController.class)
class PlanCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanConfigService planConfigService;

    @MockitoBean
    private SubscriptionProductCatalog productCatalog;

    @Test
    void returnsPlanCatalogForPaywall() throws Exception {
        when(planConfigService.allLimits()).thenReturn(Map.of(
                PlanType.FREE, new PlanLimits(PlanType.FREE, 0, 5, 20, 0, 180, new BigDecimal("0.10")),
                PlanType.PLUS, new PlanLimits(PlanType.PLUS, 0, 60, 500, 0, 300, new BigDecimal("1.20")),
                PlanType.PRO, new PlanLimits(PlanType.PRO, 0, 150, 1000, 60, 400, new BigDecimal("2.80"))
        ));
        when(productCatalog.findProductId(PlanType.PLUS)).thenReturn(Optional.of("watchmyai.plus.monthly"));
        when(productCatalog.findProductId(PlanType.PRO)).thenReturn(Optional.of("watchmyai.pro.monthly"));

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans[0].planType").value("FREE"))
                .andExpect(jsonPath("$.plans[0].dailyRequestLimit").value(5))
                .andExpect(jsonPath("$.plans[0].monthlyRequestLimit").value(20))
                .andExpect(jsonPath("$.plans[1].productId").value("watchmyai.plus.monthly"))
                .andExpect(jsonPath("$.plans[2].monthlyPremiumRequestLimit").value(60));
    }
}
