ALTER TABLE user_usage
    ALTER COLUMN estimated_monthly_cost_eur TYPE NUMERIC(12, 6);

ALTER TABLE ai_request_log
    ALTER COLUMN estimated_request_cost_eur TYPE NUMERIC(12, 6);

ALTER TABLE ai_request_log
    ALTER COLUMN estimated_monthly_cost_eur TYPE NUMERIC(12, 6);

ALTER TABLE ai_request_log
    ALTER COLUMN monthly_cost_cap_eur TYPE NUMERIC(12, 6);
