package com.watchmyai.quota;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PlanConfigService {

    private final Map<PlanType, PlanLimits> limitsByPlan = Map.of(
            PlanType.FREE, new PlanLimits(
                    PlanType.FREE,
                    20,
                    0,
                    0,
                    180,
                    0.01
            ),
            PlanType.PLUS, new PlanLimits(
                    PlanType.PLUS,
                    0,
                    1000,
                    0,
                    300,
                    2.00
            ),
            PlanType.PRO, new PlanLimits(
                    PlanType.PRO,
                    0,
                    1500,
                    100,
                    400,
                    4.50
            )
    );

    public PlanLimits getLimits(PlanType planType) {
        return limitsByPlan.getOrDefault(planType, limitsByPlan.get(PlanType.FREE));
    }
}