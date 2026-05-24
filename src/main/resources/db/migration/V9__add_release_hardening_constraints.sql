ALTER TABLE user_usage
    ADD CONSTRAINT chk_user_usage_non_negative
        CHECK (
            used_lifetime_requests >= 0
            AND used_daily_requests >= 0
            AND used_monthly_requests >= 0
            AND used_premium_requests >= 0
            AND estimated_monthly_cost_eur >= 0
        );

ALTER TABLE ai_request_log
    ADD COLUMN retained_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE ai_request_log
    ADD CONSTRAINT chk_ai_request_log_non_negative
        CHECK (
            input_tokens >= 0
            AND output_tokens >= 0
            AND remaining_requests >= 0
            AND monthly_usage_percent >= 0
            AND monthly_usage_percent <= 100
            AND estimated_request_cost_eur >= 0
            AND estimated_monthly_cost_eur >= 0
            AND monthly_cost_cap_eur >= 0
        );

CREATE INDEX idx_ai_request_log_user_created_at
    ON ai_request_log(user_id, created_at DESC);

CREATE INDEX idx_ai_request_log_status_updated_at
    ON ai_request_log(status, updated_at);
