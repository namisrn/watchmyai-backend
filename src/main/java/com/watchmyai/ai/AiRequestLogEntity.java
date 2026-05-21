package com.watchmyai.ai;

import com.watchmyai.quota.PlanType;
import com.watchmyai.quota.QuotaState;
import jakarta.persistence.*;
import java.util.Locale;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "ai_request_log",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ai_request_log_user_client_request_id",
                        columnNames = {"user_id", "client_request_id"}
                )
        }
)
@SuppressWarnings({"unused", "JpaDataSourceORMInspection", "FieldCanBeLocal"})
public class AiRequestLogEntity {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED  = "COMPLETED";
    private static final String STATUS_BLOCKED    = "BLOCKED";
    private static final String STATUS_FAILED     = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_request_id", nullable = false, length = 100)
    private String clientRequestId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "mode", nullable = false, length = 50)
    private String mode;

    @Column(name = "language", nullable = false, length = 20)
    private String language;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 50)
    private PlanType planType;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "answer", length = 4000)
    private String answer;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_request_cost_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedRequestCostEur;

    @Column(name = "request_allowed", nullable = false)
    private boolean requestAllowed;

    @Column(name = "remaining_requests", nullable = false)
    private int remainingRequests;

    @Column(name = "monthly_usage_percent", nullable = false)
    private int monthlyUsagePercent;

    @Column(name = "estimated_monthly_cost_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedMonthlyCostEur;

    @Column(name = "monthly_cost_cap_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal monthlyCostCapEur;

    @Column(name = "throttle_state", nullable = false)
    private String throttleState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiRequestLogEntity() {
    }

    private AiRequestLogEntity(
            String clientRequestId,
            String userId,
            AskAIRequest request,
            PlanType planType
    ) {
        this.clientRequestId = clientRequestId;
        this.userId = userId;
        this.source = request.source();
        this.mode = request.mode();
        this.language = request.language();
        this.status = STATUS_PROCESSING;
        this.planType = planType;
        this.modelUsed = null;
        this.answer = null;
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.estimatedRequestCostEur = BigDecimal.ZERO;
        this.requestAllowed = false;
        this.remainingRequests = 0;
        this.monthlyUsagePercent = 0;
        this.estimatedMonthlyCostEur = BigDecimal.ZERO;
        this.monthlyCostCapEur = BigDecimal.ZERO;
        this.throttleState = QuotaState.NORMAL.toApiValue();
    }

    public AiRequestLogEntity(
            String clientRequestId,
            String userId,
            AskAIResponse response
    ) {
        this.clientRequestId = clientRequestId;
        this.userId = userId;
        this.source = "unknown";
        this.mode = "unknown";
        this.language = "auto";
        this.status = STATUS_COMPLETED;
        this.planType = response.planType();
        this.modelUsed = response.modelUsed();
        this.answer = response.answer();
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.estimatedRequestCostEur = BigDecimal.ZERO;
        this.requestAllowed = response.requestAllowed();
        this.remainingRequests = response.remainingRequests();
        this.monthlyUsagePercent = response.monthlyUsagePercent();
        this.estimatedMonthlyCostEur = response.estimatedMonthlyCostEur();
        this.monthlyCostCapEur = response.monthlyCostCapEur();
        this.throttleState = response.throttleState();
    }

    public static AiRequestLogEntity processing(
            String clientRequestId,
            String userId,
            AskAIRequest request,
            PlanType planType
    ) {
        return new AiRequestLogEntity(clientRequestId, userId, request, planType);
    }

    @PrePersist
    public void markCreated() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isCompleted() {
        return !STATUS_PROCESSING.equals(status);
    }

    public AskAIResponse toInProgressResponse() {
        return new AskAIResponse(
                AskAIResponse.STATUS_PROCESSING,
                "Deine Anfrage wird verarbeitet.",
                "none",
                planType,
                false,
                remainingRequests,
                monthlyUsagePercent,
                estimatedMonthlyCostEur,
                monthlyCostCapEur,
                QuotaState.NORMAL.toApiValue()
        );
    }

    public void complete(
            AskAIResponse response,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedRequestCostEur
    ) {
        this.status = response.status().toUpperCase(Locale.ROOT);
        this.planType = response.planType();
        this.modelUsed = response.modelUsed();
        this.answer = response.answer();
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedRequestCostEur = estimatedRequestCostEur;
        this.requestAllowed = response.requestAllowed();
        this.remainingRequests = response.remainingRequests();
        this.monthlyUsagePercent = response.monthlyUsagePercent();
        this.estimatedMonthlyCostEur = response.estimatedMonthlyCostEur();
        this.monthlyCostCapEur = response.monthlyCostCapEur();
        this.throttleState = response.throttleState();
    }

    public AskAIResponse toResponse() {
        if (!isCompleted()) {
            return toInProgressResponse();
        }

        return new AskAIResponse(
                terminalStatus(),
                answer,
                modelUsed,
                planType,
                requestAllowed,
                remainingRequests,
                monthlyUsagePercent,
                estimatedMonthlyCostEur,
                monthlyCostCapEur,
                throttleState
        );
    }

    private String terminalStatus() {
        // New-style records: status column holds the actual terminal value
        if (STATUS_BLOCKED.equals(status)) return AskAIResponse.STATUS_BLOCKED;
        if (STATUS_FAILED.equals(status))  return AskAIResponse.STATUS_FAILED;
        // Legacy and new COMPLETED records: derive from requestAllowed
        if (requestAllowed) return AskAIResponse.STATUS_COMPLETED;
        // Legacy records stored status="COMPLETED" for failures — distinguish via throttleState sentinel
        return "openai_error".equals(throttleState)
                ? AskAIResponse.STATUS_FAILED
                : AskAIResponse.STATUS_BLOCKED;
    }
}
