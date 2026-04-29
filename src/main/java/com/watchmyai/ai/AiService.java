package com.watchmyai.ai;

import com.watchmyai.quota.DebugPlanService;
import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.QuotaCheckResult;
import com.watchmyai.quota.QuotaService;
import com.watchmyai.quota.UsageService;
import com.watchmyai.quota.CostEstimatorService;
import com.watchmyai.user.UserContextService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class AiService {

    private static final Duration DUPLICATE_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DUPLICATE_WAIT_INTERVAL = Duration.ofMillis(100);

    private final ModelRouter modelRouter;
    private final PromptBuilder promptBuilder;
    private final OpenAiClient openAiClient;
    private final QuotaService quotaService;
    private final UsageService usageService;
    private final DebugPlanService debugPlanService;
    private final CostEstimatorService costEstimatorService;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final UserContextService userContextService;

    public AiService(
            ModelRouter modelRouter,
            PromptBuilder promptBuilder,
            OpenAiClient openAiClient,
            QuotaService quotaService,
            UsageService usageService,
            DebugPlanService debugPlanService,
            CostEstimatorService costEstimatorService,
            AiRequestLogRepository aiRequestLogRepository,
            UserContextService userContextService
    ) {
        this.modelRouter = modelRouter;
        this.promptBuilder = promptBuilder;
        this.openAiClient = openAiClient;
        this.quotaService = quotaService;
        this.usageService = usageService;
        this.debugPlanService = debugPlanService;
        this.costEstimatorService = costEstimatorService;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.userContextService = userContextService;
    }

    public AskAIResponse ask(AskAIRequest request) {
        String userId = userContextService.getCurrentUser().userId();

        return findExistingResponse(userId, request.clientRequestId())
                .orElseGet(() -> reserveAndProcessRequest(request, userId));
    }

    private Optional<AskAIResponse> findExistingResponse(String userId, String clientRequestId) {
        return aiRequestLogRepository
                .findByUserIdAndClientRequestId(userId, clientRequestId)
                .map(log -> log.isCompleted()
                        ? log.toResponse()
                        : waitForCompletedResponse(userId, clientRequestId, log));
    }

    private AskAIResponse reserveAndProcessRequest(AskAIRequest request, String userId) {
        // Development fallback: current plan is controlled through DebugPlanService until subscriptions are integrated.
        // User identity can be supplied per request while the real authentication layer is still pending.
        PlanType currentPlan = debugPlanService.getCurrentPlan();

        AiRequestLogEntity requestLog;
        try {
            requestLog = aiRequestLogRepository.saveAndFlush(
                    AiRequestLogEntity.processing(
                            request.clientRequestId(),
                            userId,
                            request,
                            currentPlan
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            return waitForCompletedResponse(userId, request.clientRequestId(), null);
        }

        return processReservedRequest(request, currentPlan, requestLog);
    }

    private AskAIResponse waitForCompletedResponse(
            String userId,
            String clientRequestId,
            AiRequestLogEntity fallbackLog
    ) {
        Instant deadline = Instant.now().plus(DUPLICATE_WAIT_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            Optional<AiRequestLogEntity> existingLog = aiRequestLogRepository
                    .findByUserIdAndClientRequestId(userId, clientRequestId)
                    .filter(AiRequestLogEntity::isCompleted);

            if (existingLog.isPresent()) {
                return existingLog.get().toResponse();
            }

            sleepBeforeRetry();
        }

        return fallbackLog == null
                ? new AskAIResponse(
                "Deine Anfrage wird bereits verarbeitet. Bitte versuche es gleich erneut.",
                "none",
                PlanType.FREE,
                false,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "processing"
        )
                : fallbackLog.toInProgressResponse();
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(DUPLICATE_WAIT_INTERVAL.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private AskAIResponse processReservedRequest(
            AskAIRequest request,
            PlanType currentPlan,
            AiRequestLogEntity requestLog
    ) {
        QuotaCheckResult quota = quotaService.checkQuota(currentPlan);

        if (!quota.requestAllowed()) {
            AskAIResponse blockedResponse = new AskAIResponse(
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

            completeRequestLog(requestLog, blockedResponse, 0, 0, BigDecimal.ZERO);
            return blockedResponse;
        }

        boolean usePremiumModel = shouldUsePremiumModel(request, currentPlan, quota);
        String model = modelRouter.selectModel(request, currentPlan, usePremiumModel);
        String systemPrompt = promptBuilder.buildSystemPrompt(
                request.mode(),
                quota.limits().maxOutputTokens()
        );
        String userPrompt = promptBuilder.buildUserPrompt(request);
        OpenAiResponse openAiResponse;
        try {
            openAiResponse = openAiClient.ask(
                    model,
                    systemPrompt,
                    userPrompt,
                    quota.limits().maxOutputTokens()
            );
        } catch (OpenAiClientException exception) {
            AskAIResponse failedResponse = new AskAIResponse(
                    exception.getMessage(),
                    model,
                    currentPlan,
                    false,
                    quota.remainingRequests(),
                    quota.monthlyUsagePercent(),
                    quota.estimatedMonthlyCostEur(),
                    quota.monthlyCostCapEur(),
                    "openai_error"
            );

            completeRequestLog(requestLog, failedResponse, 0, 0, BigDecimal.ZERO);
            throw exception;
        }

        String answer = openAiResponse.answer();
        int inputTokens = openAiResponse.inputTokens();
        int outputTokens = openAiResponse.outputTokens();
        BigDecimal estimatedRequestCostEur = costEstimatorService.estimateCostEur(model, inputTokens, outputTokens);

        usageService.recordRequest(currentPlan, estimatedRequestCostEur, usePremiumModel);
        QuotaCheckResult updatedQuota = quotaService.checkQuota(currentPlan);

        AskAIResponse response = new AskAIResponse(
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

        completeRequestLog(requestLog, response, inputTokens, outputTokens, estimatedRequestCostEur);
        return response;
    }

    private void completeRequestLog(
            AiRequestLogEntity requestLog,
            AskAIResponse response,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedRequestCostEur
    ) {
        requestLog.complete(response, inputTokens, outputTokens, estimatedRequestCostEur);
        aiRequestLogRepository.save(requestLog);
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
