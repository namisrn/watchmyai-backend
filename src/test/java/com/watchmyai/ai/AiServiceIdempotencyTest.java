package com.watchmyai.ai;

import com.watchmyai.quota.CostEstimatorService;
import com.watchmyai.quota.DebugPlanService;
import com.watchmyai.quota.PlanLimits;
import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.QuotaCheckResult;
import com.watchmyai.quota.QuotaService;
import com.watchmyai.quota.UsageService;
import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private ModelRouter modelRouter;
    private PromptBuilder promptBuilder;
    private OpenAiClient openAiClient;
    private QuotaService quotaService;
    private UsageService usageService;
    private DebugPlanService debugPlanService;
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
        debugPlanService = mock(DebugPlanService.class);
        costEstimatorService = mock(CostEstimatorService.class);
        aiRequestLogRepository = mock(AiRequestLogRepository.class);
        UserContextService userContextService = mock(UserContextService.class);

        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity(USER_ID));
        when(aiRequestLogRepository.saveAndFlush(any(AiRequestLogEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, AiRequestLogEntity.class));

        aiService = new AiService(
                modelRouter,
                promptBuilder,
                openAiClient,
                quotaService,
                usageService,
                debugPlanService,
                costEstimatorService,
                aiRequestLogRepository,
                userContextService
        );
    }

    @Test
    void askReturnsStoredResponseForDuplicateClientRequestId() {
        AskAIRequest request = validRequest();
        AskAIResponse storedResponse = new AskAIResponse(
                "Gespeicherte Antwort",
                "gpt-5.4-mini",
                PlanType.FREE,
                true,
                14,
                30,
                new BigDecimal("0.003000"),
                new BigDecimal("0.010000"),
                "normal"
        );
        AiRequestLogEntity storedLog = new AiRequestLogEntity(
                CLIENT_REQUEST_ID,
                USER_ID,
                storedResponse
        );

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.of(storedLog));

        AskAIResponse response = aiService.ask(request);

        assertThat(response).isEqualTo(storedResponse);
        verify(aiRequestLogRepository).findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID);
        verifyNoInteractions(
                modelRouter,
                promptBuilder,
                openAiClient,
                quotaService,
                usageService,
                debugPlanService,
                costEstimatorService
        );
        verify(aiRequestLogRepository, never()).save(any(AiRequestLogEntity.class));
        verify(aiRequestLogRepository, never()).saveAndFlush(any(AiRequestLogEntity.class));
    }

    @Test
    void askWaitsForStoredResponseWhenReservationAlreadyExists() {
        AskAIRequest request = validRequest();
        AskAIResponse storedResponse = new AskAIResponse(
                "Antwort aus paralleler Anfrage",
                "gpt-5.4-mini",
                PlanType.FREE,
                true,
                14,
                30,
                new BigDecimal("0.003000"),
                new BigDecimal("0.010000"),
                "normal"
        );
        AiRequestLogEntity storedLog = new AiRequestLogEntity(
                CLIENT_REQUEST_ID,
                USER_ID,
                storedResponse
        );

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty(), Optional.of(storedLog));
        when(debugPlanService.getCurrentPlan())
                .thenReturn(PlanType.FREE);
        doThrow(new DataIntegrityViolationException("duplicate client request id"))
                .when(aiRequestLogRepository)
                .saveAndFlush(any(AiRequestLogEntity.class));

        AskAIResponse response = aiService.ask(request);

        assertThat(response).isEqualTo(storedResponse);
        verifyNoInteractions(
                modelRouter,
                promptBuilder,
                openAiClient,
                quotaService,
                usageService,
                costEstimatorService
        );
    }

    @Test
    void askProcessesAndStoresNewAllowedRequest() {
        AskAIRequest request = validRequest();
        PlanLimits limits = new PlanLimits(PlanType.FREE, 20, 0, 0, 180, new BigDecimal("0.010000"));
        QuotaCheckResult initialQuota = quotaResult(true, 15, 25, limits);
        QuotaCheckResult updatedQuota = quotaResult(true, 14, 30, limits);

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(debugPlanService.getCurrentPlan())
                .thenReturn(PlanType.FREE);
        when(quotaService.checkQuota(PlanType.FREE))
                .thenReturn(initialQuota, updatedQuota);
        when(modelRouter.selectModel(request, PlanType.FREE, false))
                .thenReturn("gpt-5.4-mini");
        when(promptBuilder.buildSystemPrompt("short_answer", 180))
                .thenReturn("system prompt");
        when(promptBuilder.buildUserPrompt(request))
                .thenReturn("Hallo");
        when(openAiClient.ask("gpt-5.4-mini", "system prompt", "Hallo", 180))
                .thenReturn(new OpenAiResponse("Neue Antwort", 8, 3));
        when(costEstimatorService.estimateCostEur("gpt-5.4-mini", 8, 3))
                .thenReturn(new BigDecimal("0.000020"));

        AskAIResponse response = aiService.ask(request);

        assertThat(response.answer()).isEqualTo("Neue Antwort");
        assertThat(response.modelUsed()).isEqualTo("gpt-5.4-mini");
        assertThat(response.requestAllowed()).isTrue();
        assertThat(response.remainingRequests()).isEqualTo(14);
        assertThat(response.monthlyUsagePercent()).isEqualTo(30);

        verify(usageService).recordRequest(PlanType.FREE, new BigDecimal("0.000020"), false);
        verify(aiRequestLogRepository).save(any(AiRequestLogEntity.class));
    }

    @Test
    void askStoresBlockedResponseWithoutCallingOpenAiOrRecordingUsage() {
        AskAIRequest request = validRequest();
        PlanLimits limits = new PlanLimits(PlanType.FREE, 20, 0, 0, 180, new BigDecimal("0.010000"));
        QuotaCheckResult cappedQuota = quotaResult(false, 0, 100, limits);

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(debugPlanService.getCurrentPlan())
                .thenReturn(PlanType.FREE);
        when(quotaService.checkQuota(PlanType.FREE))
                .thenReturn(cappedQuota);

        AskAIResponse response = aiService.ask(request);

        assertThat(response.requestAllowed()).isFalse();
        assertThat(response.modelUsed()).isEqualTo("none");
        assertThat(response.remainingRequests()).isZero();

        verifyNoInteractions(modelRouter, promptBuilder, openAiClient, costEstimatorService);
        verifyNoInteractions(usageService);
        verify(aiRequestLogRepository).save(any(AiRequestLogEntity.class));
    }

    @Test
    void askDoesNotRecordUsageWhenOpenAiFails() {
        AskAIRequest request = validRequest();
        PlanLimits limits = new PlanLimits(PlanType.FREE, 20, 0, 0, 180, new BigDecimal("0.010000"));
        QuotaCheckResult initialQuota = quotaResult(true, 15, 25, limits);

        when(aiRequestLogRepository.findByUserIdAndClientRequestId(USER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(debugPlanService.getCurrentPlan())
                .thenReturn(PlanType.FREE);
        when(quotaService.checkQuota(PlanType.FREE))
                .thenReturn(initialQuota);
        when(modelRouter.selectModel(request, PlanType.FREE, false))
                .thenReturn("gpt-5.4-mini");
        when(promptBuilder.buildSystemPrompt("short_answer", 180))
                .thenReturn("system prompt");
        when(promptBuilder.buildUserPrompt(request))
                .thenReturn("Hallo");
        when(openAiClient.ask("gpt-5.4-mini", "system prompt", "Hallo", 180))
                .thenThrow(new OpenAiClientException("OpenAI-Fehler: invalid_api_key", 401));

        assertThatThrownBy(() -> aiService.ask(request))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessage("OpenAI-Fehler: invalid_api_key");

        verifyNoInteractions(costEstimatorService);
        verifyNoInteractions(usageService);
        verify(aiRequestLogRepository).save(any(AiRequestLogEntity.class));
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

    private QuotaCheckResult quotaResult(
            boolean requestAllowed,
            int remainingRequests,
            int monthlyUsagePercent,
            PlanLimits limits
    ) {
        return new QuotaCheckResult(
                limits.planType(),
                requestAllowed,
                remainingRequests,
                0,
                limits.monthlyPremiumRequestLimit(),
                monthlyUsagePercent,
                new BigDecimal("0.002000"),
                limits.monthlyCostCapEur(),
                requestAllowed ? "normal" : "capped",
                limits
        );
    }
}
