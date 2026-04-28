CREATE TABLE user_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    plan_type VARCHAR(50) NOT NULL,
    period_year_month VARCHAR(7) NOT NULL,
    used_lifetime_requests INTEGER NOT NULL,
    used_monthly_requests INTEGER NOT NULL,
    used_premium_requests INTEGER NOT NULL,
    estimated_monthly_cost_eur DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_usage_user_period UNIQUE (user_id, period_year_month)
);