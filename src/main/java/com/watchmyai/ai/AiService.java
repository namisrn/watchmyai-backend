package com.watchmyai.ai;

import com.watchmyai.quota.DebugPlanService;
import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.QuotaCheckResult;
import com.watchmyai.quota.QuotaService;
import com.watchmyai.quota.UsageService;
import com.watchmyai.quota.CostEstimatorService;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final ModelRouter modelRouter;
    private final PromptBuilder promptBuilder;
    private final OpenAiClient openAiClient;
    private final QuotaService quotaService;
    private final UsageService usageService;
    private final DebugPlanService debugPlanService;
    private final CostEstimatorService costEstimatorService;

    public AiService(
            ModelRouter modelRouter,
            PromptBuilder promptBuilder,
            OpenAiClient openAiClient,
            QuotaService quotaService,
            UsageService usageService,
            DebugPlanService debugPlanService,
            CostEstimatorService costEstimatorService
    ) {
        this.modelRouter = modelRouter;
        this.promptBuilder = promptBuilder;
        this.openAiClient = openAiClient;
        this.quotaService = quotaService;
        this.usageService = usageService;
        this.debugPlanService = debugPlanService;
        this.costEstimatorService = costEstimatorService;
    }

    public AskAIResponse ask(AskAIRequest request) {
        // TODO: Später aus echtem User-/Subscription-Kontext laden.
        PlanType currentPlan = debugPlanService.getCurrentPlan();
        QuotaCheckResult quota = quotaService.checkQuota(currentPlan);

        if (!quota.requestAllowed()) {
            return new AskAIResponse(
                    "Dein aktuelles Limit ist erreicht. Bitte upgrade oder versuche es später erneut.",
                    "none",
                    currentPlan,
                    false,
                    quota.remainingRequests(),
                    quota.monthlyUsagePercent(),
                    quota.estimatedMonthlyCostEur(),
                    quota.monthlyCostCapEur(),
                    quota.throttleState()
            );
        }

        boolean usePremiumModel = shouldUsePremiumModel(request, currentPlan, quota);
        String model = modelRouter.selectModel(request, currentPlan, usePremiumModel);
        String systemPrompt = promptBuilder.buildSystemPrompt(
                request.mode(),
                quota.limits().maxOutputTokens()
        );
        String userPrompt = promptBuilder.buildUserPrompt(request);
        String answer = openAiClient.ask(
                model,
                systemPrompt,
                userPrompt,
                quota.limits().maxOutputTokens()
        );

        int inputTokens = costEstimatorService.estimateInputTokens(systemPrompt, userPrompt);
        int outputTokens = costEstimatorService.estimateOutputTokens(answer);
        double estimatedRequestCostEur = costEstimatorService.estimateCostEur(model, inputTokens, outputTokens);

        usageService.recordRequest(currentPlan, estimatedRequestCostEur, usePremiumModel);
        QuotaCheckResult updatedQuota = quotaService.checkQuota(currentPlan);

        return new AskAIResponse(
                answer,
                model,
                currentPlan,
                true,
                updatedQuota.remainingRequests(),
                updatedQuota.monthlyUsagePercent(),
                updatedQuota.estimatedMonthlyCostEur(),
                updatedQuota.monthlyCostCapEur(),
                updatedQuota.throttleState()
        );
    }
    private boolean shouldUsePremiumModel(
            AskAIRequest request,
            PlanType currentPlan,
            QuotaCheckResult quota
    ) {
        boolean premiumRequested = "premium_reasoning".equals(request.mode());
        boolean proUser = currentPlan == PlanType.PRO;
        boolean premiumRequestsAvailable = quota.usedPremiumRequests() < quota.monthlyPremiumRequestLimit();

        return premiumRequested && proUser && premiumRequestsAvailable;
    }
}