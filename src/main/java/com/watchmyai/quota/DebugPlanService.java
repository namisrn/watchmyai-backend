package com.watchmyai.quota;

import org.springframework.stereotype.Service;

@Service
public class DebugPlanService {

    private PlanType currentPlan = PlanType.FREE;

    public PlanType getCurrentPlan() {
        return currentPlan;
    }

    public void setCurrentPlan(PlanType currentPlan) {
        this.currentPlan = currentPlan;
    }
}
