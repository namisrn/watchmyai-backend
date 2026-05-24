package com.watchmyai.ai;

import com.watchmyai.quota.CostEstimatorService;
import com.watchmyai.quota.UserPlanService;
import com.watchmyai.quota.PlanLimits;
import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.QuotaCheckResult;
import com.watchmyai.quota.QuotaService;
import com.watchmyai.quota.QuotaState;
import com.watchmyai.quota.UsageService;
import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AiServiceIdempotencyTest {

    private static final String USER_ID = "test-user";
    private static final String CLIENT_REQUEST_ID = "client-request-001";
    private static final PlanLimits FREE_LIMITS =
            new PlanLimits(PlanType.FREE, 20, 5, 20, 0, 180, new BigDecimal("0.010000"));

    private ModelRouter modelRouter;
    private PromptBuilder promptBuilder;
    private OpenAiClient openAiClient;
    private QuotaService quotaService;
    private UsageService usageService;
    private UserPlanService userPlanService;
    private CostEstimatorService costEstimatorService;
    private AiRequestLogRepository aiRequestLogRepository;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        modelRouter = mock(ModelRouter.class);
        promptBuilder = mock(PromptBuilder.class);
        openAiClient = mock(OpenAiClient.class);
        quotaService = mock(QuotaService.class);
        usageService = mock(UsageService.class);
        userPlanService = mock(UserPlanService.class);
        costEstimatorService = mock(CostEstimatorService.class);
        aiRequestLogRepository = mock(AiRequestLogRepository.class);
        UserContextService userContextService = mock(UserContextService.class);

        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity(USER_ID));
        when(aiRequestLogRepository.saveAndFlush(any(AiRequestLogEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, AiRequestLogEntity.class));

        // Synchronous executor: the worker job runs inline so the test can assert the full flow.
        Executor synchronousExecutor = Runnable::run;

        aiService = new AiService(
                modelRouter,
                promptBuilder,
                openAiClient,
                quotaService,
                usageService,
                userPlanService,
                costEstimatorService,
                aiRequestLogRepository,
                userContextService,
                synchronousExecutor,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void askReturnsStoredResultForCompletedDuplicate() {
        AskAIResponse storedResponse = completedResponse("Gespeicherte Antwort");
        AiRequestLogEntity storedLog = new AiRequestLogEntity(CLIENT_REQUEST_ID, USER_ID, storedResponse);

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.of(storedLog));

        AskAIResponse response = aiService.ask(validRequest());

        assertThat(response.status()).isEqualTo(AskAIResponse.STATUS_COMPLETED);
        assertThat(response.answer()).isEqualTo("Gespeicherte Antwort");
        verifyNoInteractions(modelRouter, promptBuilder, openAiClient, quotaService, usageService, userPlanService);
        verify(aiRequestLogRepository, never()).saveAndFlush(any(AiRequestLogEntity.class));
    }

    @Test
    void askReturnsProcessingForInFlightDuplicate() {
        AiRequestLogEntity processingLog = AiRequestLogEntity.processing(
                CLIENT_REQUEST_ID, USER_ID, validRequest(), PlanType.FREE);
        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.of(processingLog));

        AskAIResponse response = aiService.ask(validRequest());

        assertThat(response.status()).isEqualTo(AskAIResponse.STATUS_PROCESSING);
        verifyNoInteractions(openAiClient, usageService);
    }

    @Test
    void askHandlesConcurrentDuplicateInsert() {
        AiRequestLogEntity processingLog = AiRequestLogEntity.processing(
                CLIENT_REQUEST_ID, USER_ID, validRequest(), PlanType.FREE);
        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty(), Optional.of(processingLog));
        when(userPlanService.getCurrentPlan()).thenReturn(PlanType.FREE);
        doThrow(new DataIntegrityViolationException("duplicate client request id"))
                .when(aiRequestLogRepository)
                .saveAndFlush(any(AiRequestLogEntity.class));

        AskAIResponse response = aiService.ask(validRequest());

        assertThat(response.status()).isEqualTo(AskAIResponse.STATUS_PROCESSING);
        verifyNoInteractions(openAiClient, usageService);
    }

    @Test
    void askAcceptsNewRequestAndProcessesViaWorker() {
        AskAIRequest request = validRequest();
        AiRequestLogEntity processingLog = AiRequestLogEntity.processing(
                CLIENT_REQUEST_ID, USER_ID, request, PlanType.FREE);
        QuotaCheckResult initialQuota = quotaResult(true, 15, 25);
        QuotaCheckResult updatedQuota = quotaResult(true, 14, 30);

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(userPlanService.getCurrentPlan()).thenReturn(PlanType.FREE);
        when(aiRequestLogRepository.findById(any())).thenReturn(Optional.of(processingLog));
        when(usageService.reserveRequest(USER_ID, PlanType.FREE)).thenReturn(true);
        when(quotaService.checkQuota(USER_ID, PlanType.FREE)).thenReturn(initialQuota, updatedQuota);
        when(modelRouter.selectModel(request, PlanType.FREE, false)).thenReturn("gpt-5.4-mini");
        when(promptBuilder.buildSystemPrompt("short_answer", 180)).thenReturn("system prompt");
        when(promptBuilder.buildUserPrompt(request)).thenReturn("Hallo");
        when(openAiClient.ask("gpt-5.4-mini", "system prompt", "Hallo", 180))
                .thenReturn(new OpenAiResponse("Neue Antwort", 8, 3));
        when(costEstimatorService.estimateCostEur("gpt-5.4-mini", 8, 3))
                .thenReturn(new BigDecimal("0.000020"));

        AskAIResponse response = aiService.ask(request);

        assertThat(response.status()).isEqualTo(AskAIResponse.STATUS_PROCESSING);
        verify(usageService).reserveRequest(USER_ID, PlanType.FREE);
        verify(openAiClient).ask("gpt-5.4-mini", "system prompt", "Hallo", 180);
        verify(usageService).finalizeRequest(USER_ID, PlanType.FREE, new BigDecimal("0.000020"));
        verify(usageService, never()).refundRequest(any(), any());
        verify(aiRequestLogRepository).save(any(AiRequestLogEntity.class));
    }

    @Test
    void askBlocksNewRequestWhenQuotaExhausted() {
        AskAIRequest request = validRequest();
        AiRequestLogEntity processingLog = AiRequestLogEntity.processing(
                CLIENT_REQUEST_ID, USER_ID, request, PlanType.FREE);

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(userPlanService.getCurrentPlan()).thenReturn(PlanType.FREE);
        when(usageService.reserveRequest(USER_ID, PlanType.FREE)).thenReturn(false);
        when(quotaService.checkQuota(USER_ID, PlanType.FREE)).thenReturn(quotaResult(false, 0, 100));

        AskAIResponse response = aiService.ask(request);

        assertThat(response.status()).isEqualTo(AskAIResponse.STATUS_BLOCKED);
        assertThat(response.requestAllowed()).isFalse();
        verifyNoInteractions(modelRouter, promptBuilder, openAiClient, costEstimatorService);
        verify(usageService).reserveRequest(USER_ID, PlanType.FREE);
        verify(usageService, never()).finalizeRequest(any(), any(), any());
        verify(usageService, never()).refundRequest(any(), any());
        verify(aiRequestLogRepository).save(any(AiRequestLogEntity.class));
    }

    @Test
    void workerRefundsAndMarksFailedWhenOpenAiFails() {
        AskAIRequest request = validRequest();
        AiRequestLogEntity processingLog = AiRequestLogEntity.processing(
                CLIENT_REQUEST_ID, USER_ID, request, PlanType.FREE);

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(userPlanService.getCurrentPlan()).thenReturn(PlanType.FREE);
        when(aiRequestLogRepository.findById(any())).thenReturn(Optional.of(processingLog));
        when(usageService.reserveRequest(USER_ID, PlanType.FREE)).thenReturn(true);
        when(quotaService.checkQuota(USER_ID, PlanType.FREE)).thenReturn(quotaResult(true, 15, 25));
        when(modelRouter.selectModel(request, PlanType.FREE, false)).thenReturn("gpt-5.4-mini");
        when(promptBuilder.buildSystemPrompt("short_answer", 180)).thenReturn("system prompt");
        when(promptBuilder.buildUserPrompt(request)).thenReturn("Hallo");
        when(openAiClient.ask("gpt-5.4-mini", "system prompt", "Hallo", 180))
                .thenThrow(new OpenAiClientException("OpenAI-Fehler: invalid_api_key", 401));

        AskAIResponse response = aiService.ask(request);

        assertThat(response.status()).isEqualTo(AskAIResponse.STATUS_PROCESSING);
        verify(usageService).refundRequest(USER_ID, PlanType.FREE);
        verify(usageService, never()).finalizeRequest(any(), any(), any());
        verify(costEstimatorService, never()).estimateCostEur(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(aiRequestLogRepository).save(any(AiRequestLogEntity.class));
    }

    @Test
    void jobStatusReturnsStoredResult() {
        AiRequestLogEntity storedLog = new AiRequestLogEntity(
                CLIENT_REQUEST_ID, USER_ID, completedResponse("Antwort"));
        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.of(storedLog));

        AskAIResponse response = aiService.jobStatus(CLIENT_REQUEST_ID);

        assertThat(response.status()).isEqualTo(AskAIResponse.STATUS_COMPLETED);
        assertThat(response.answer()).isEqualTo("Antwort");
    }

    @Test
    void jobStatusThrowsWhenJobMissing() {
        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiService.jobStatus(CLIENT_REQUEST_ID))
                .isInstanceOf(AiJobNotFoundException.class);
    }

    private AskAIRequest validRequest() {
        return new AskAIRequest(
                "Hallo",
                "watch",
                "short_answer",
                "de",
                CLIENT_REQUEST_ID
        );
    }

    private AskAIResponse completedResponse(String answer) {
        return new AskAIResponse(
                AskAIResponse.STATUS_COMPLETED,
                answer,
                "gpt-5.4-mini",
                PlanType.FREE,
                true,
                14,
                30,
                new BigDecimal("0.003000"),
                new BigDecimal("0.010000"),
                "normal"
        );
    }

    private QuotaCheckResult quotaResult(
            boolean requestAllowed,
            int remainingRequests,
            int monthlyUsagePercent
    ) {
        return new QuotaCheckResult(
                PlanType.FREE,
                requestAllowed,
                remainingRequests,
                Math.min(remainingRequests, FREE_LIMITS.dailyRequestLimit()),
                FREE_LIMITS.dailyRequestLimit(),
                monthlyUsagePercent,
                remainingRequests,
                FREE_LIMITS.monthlyRequestLimit(),
                0,
                FREE_LIMITS.monthlyPremiumRequestLimit(),
                monthlyUsagePercent,
                new BigDecimal("0.002000"),
                FREE_LIMITS.monthlyCostCapEur(),
                requestAllowed ? QuotaState.NORMAL : QuotaState.CAPPED,
                FREE_LIMITS
        );
    }
}
