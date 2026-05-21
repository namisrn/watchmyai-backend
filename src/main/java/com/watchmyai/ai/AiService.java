package com.watchmyai.ai;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.QuotaCheckResult;
import com.watchmyai.quota.QuotaService;
import com.watchmyai.quota.QuotaState;
import com.watchmyai.quota.UsageService;
import com.watchmyai.quota.CostEstimatorService;
import com.watchmyai.quota.UserPlanService;
import com.watchmyai.user.UserContextService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Job-based AI request handling. {@code POST /ai/ask} reserves quota synchronously and then
 * hands the slow OpenAI call to a bounded worker pool, returning immediately with a job whose
 * status the client polls via {@code GET /ai/ask/{clientRequestId}}.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final ModelRouter modelRouter;
    private final PromptBuilder promptBuilder;
    private final OpenAiClient openAiClient;
    private final QuotaService quotaService;
    private final UsageService usageService;
    private final UserPlanService userPlanService;
    private final CostEstimatorService costEstimatorService;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final UserContextService userContextService;
    private final Executor aiJobExecutor;
    private final Counter quotaBlockedCounter;

    public AiService(
            ModelRouter modelRouter,
            PromptBuilder promptBuilder,
            OpenAiClient openAiClient,
            QuotaService quotaService,
            UsageService usageService,
            UserPlanService userPlanService,
            CostEstimatorService costEstimatorService,
            AiRequestLogRepository aiRequestLogRepository,
            UserContextService userContextService,
            @Qualifier("aiJobExecutor") Executor aiJobExecutor,
            MeterRegistry meterRegistry
    ) {
        this.modelRouter = modelRouter;
        this.promptBuilder = promptBuilder;
        this.openAiClient = openAiClient;
        this.quotaService = quotaService;
        this.usageService = usageService;
        this.userPlanService = userPlanService;
        this.costEstimatorService = costEstimatorService;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.userContextService = userContextService;
        this.aiJobExecutor = aiJobExecutor;
        this.quotaBlockedCounter = meterRegistry.counter("watchmyai.ai.quota_blocked");
    }

    /**
     * Submits an AI request. Returns the job state immediately: {@code processing} when the
     * request was accepted and the OpenAI call runs asynchronously, {@code blocked} when a quota
     * limit is exhausted, or the stored result when the {@code clientRequestId} is a duplicate.
     */
    public AskAIResponse ask(AskAIRequest request) {
        String userId = userContextService.getCurrentUser().userId();

        return findExistingJob(userId, request.clientRequestId())
                .orElseGet(() -> submitNewRequest(request, userId));
    }

    /**
     * Returns the current state of a previously submitted job. Used for client polling.
     */
    public AskAIResponse jobStatus(String clientRequestId) {
        String userId = userContextService.getCurrentUser().userId();

        return aiRequestLogRepository
                .findByUserIdAndClientRequestId(userId, clientRequestId)
                .map(AiRequestLogEntity::toResponse)
                .orElseThrow(() -> new AiJobNotFoundException(clientRequestId));
    }

    private Optional<AskAIResponse> findExistingJob(String userId, String clientRequestId) {
        return aiRequestLogRepository
                .findByUserIdAndClientRequestId(userId, clientRequestId)
                .map(AiRequestLogEntity::toResponse);
    }

    private AskAIResponse submitNewRequest(AskAIRequest request, String userId) {
        PlanType currentPlan = userPlanService.getCurrentPlan();

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
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent request with the same clientRequestId created the job first.
            return findExistingJob(userId, request.clientRequestId())
                    .orElseGet(() -> acceptedResponse(currentPlan));
        }

        // Reserve quota synchronously so the client gets immediate "blocked" feedback.
        if (!usageService.reserveRequest(userId, currentPlan)) {
            quotaBlockedCounter.increment();
            QuotaCheckResult quota = quotaService.checkQuota(userId, currentPlan);
            log.info(
                    "AI request blocked by quota userId={} plan={} remaining={} throttleState={}",
                    userId,
                    currentPlan,
                    quota.remainingRequests(),
                    quota.throttleState()
            );
            AskAIResponse blocked = blockedResponse(currentPlan, quota);
            completeRequestLog(requestLog, blocked, 0, 0, BigDecimal.ZERO);
            return blocked;
        }

        Long logId = requestLog.getId();
        aiJobExecutor.execute(() -> processJob(request, userId, currentPlan, logId));
        return acceptedResponse(currentPlan);
    }

    /**
     * Runs on a worker thread. Performs the OpenAI call and finalizes (or refunds) the quota.
     * Must not touch the request-scoped {@link UserContextService}; the user id is passed in.
     */
    private void processJob(AskAIRequest request, String userId, PlanType currentPlan, Long logId) {
        AiRequestLogEntity requestLog = aiRequestLogRepository.findById(logId).orElse(null);
        if (requestLog == null) {
            usageService.refundRequest(userId, currentPlan);
            log.warn("AI job log vanished before processing userId={} logId={} clientRequestId={}", userId, logId, request.clientRequestId());
            return;
        }

        QuotaCheckResult quota = quotaService.checkQuota(userId, currentPlan);
        boolean usePremiumModel = shouldUsePremiumModel(request, currentPlan, quota);
        String model = modelRouter.selectModel(request, currentPlan, usePremiumModel);

        try {
            String systemPrompt = promptBuilder.buildSystemPrompt(request.mode(), quota.limits().maxOutputTokens());
            String userPrompt = promptBuilder.buildUserPrompt(request);

            OpenAiResponse openAiResponse = openAiClient.ask(
                    model,
                    systemPrompt,
                    userPrompt,
                    quota.limits().maxOutputTokens()
            );

            int inputTokens = openAiResponse.inputTokens();
            int outputTokens = openAiResponse.outputTokens();
            BigDecimal estimatedRequestCostEur = costEstimatorService.estimateCostEur(model, inputTokens, outputTokens);

            usageService.finalizeRequest(userId, currentPlan, estimatedRequestCostEur, usePremiumModel);
            QuotaCheckResult updatedQuota = quotaService.checkQuota(userId, currentPlan);

            AskAIResponse response = new AskAIResponse(
                    AskAIResponse.STATUS_COMPLETED,
                    openAiResponse.answer(),
                    model,
                    currentPlan,
                    true,
                    updatedQuota.remainingRequests(),
                    updatedQuota.monthlyUsagePercent(),
                    updatedQuota.estimatedMonthlyCostEur(),
                    updatedQuota.monthlyCostCapEur(),
                    updatedQuota.throttleState().toApiValue()
            );
            completeRequestLog(requestLog, response, inputTokens, outputTokens, estimatedRequestCostEur);
        } catch (RuntimeException exception) {
            // OpenAI failure (or any unexpected error): refund the reserved slot and mark the
            // job failed so the polling client receives a terminal error instead of hanging.
            usageService.refundRequest(userId, currentPlan);
            String message = exception instanceof OpenAiClientException
                    ? exception.getMessage()
                    : "Die Anfrage konnte nicht verarbeitet werden.";
            log.warn("AI job failed userId={} logId={} clientRequestId={} model={}", userId, logId, request.clientRequestId(), model, exception);
            AskAIResponse failed = new AskAIResponse(
                    AskAIResponse.STATUS_FAILED,
                    message,
                    model,
                    currentPlan,
                    false,
                    quota.remainingRequests(),
                    quota.monthlyUsagePercent(),
                    quota.estimatedMonthlyCostEur(),
                    quota.monthlyCostCapEur(),
                    quota.throttleState().toApiValue()
            );
            completeRequestLog(requestLog, failed, 0, 0, BigDecimal.ZERO);
        }
    }

    private AskAIResponse acceptedResponse(PlanType plan) {
        return new AskAIResponse(
                AskAIResponse.STATUS_PROCESSING,
                "Deine Anfrage wird verarbeitet.",
                "none",
                plan,
                false,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                QuotaState.NORMAL.toApiValue()
        );
    }

    private AskAIResponse blockedResponse(PlanType plan, QuotaCheckResult quota) {
        return new AskAIResponse(
                AskAIResponse.STATUS_BLOCKED,
                "Your current limit has been reached. Please upgrade or try again later.",
                "none",
                plan,
                false,
                quota.remainingRequests(),
                quota.monthlyUsagePercent(),
                quota.estimatedMonthlyCostEur(),
                quota.monthlyCostCapEur(),
                quota.throttleState().toApiValue()
        );
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
