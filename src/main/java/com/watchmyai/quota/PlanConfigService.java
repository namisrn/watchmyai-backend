package com.watchmyai.quota;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PlanConfigService {

    private final Map<PlanType, PlanLimits> limitsByPlan = Map.of(
            PlanType.FREE, new PlanLimits(
                    PlanType.FREE,
                    0,
                    5,
                    20,
                    0,
                    180,
                    new BigDecimal("0.20")
            ),
            PlanType.PLUS, new PlanLimits(
                    PlanType.PLUS,
                    0,
                    100,
                    1000,
                    0,
                    300,
                    new BigDecimal("2.00")
            ),
            PlanType.PRO, new PlanLimits(
                    PlanType.PRO,
                    0,
                    200,
                    1500,
                    100,
                    400,
                    new BigDecimal("4.50")
            )
    );

    public PlanLimits getLimits(PlanType planType) {
        return limitsByPlan.getOrDefault(planType, limitsByPlan.get(PlanType.FREE));
    }
}
