package com.watchmyai.quota;

import com.watchmyai.config.PlanCatalogProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PlanConfigService {

    private final Map<PlanType, PlanLimits> limitsByPlan;

    @Autowired
    public PlanConfigService(PlanCatalogProperties planCatalogProperties) {
        this.limitsByPlan = planCatalogProperties.toLimitsByPlan();
    }

    public PlanConfigService() {
        this(PlanCatalogProperties.defaults());
    }

    public PlanLimits getLimits(PlanType planType) {
        return limitsByPlan.getOrDefault(planType, limitsByPlan.get(PlanType.FREE));
    }

    public Map<PlanType, PlanLimits> allLimits() {
        return limitsByPlan;
    }
}
