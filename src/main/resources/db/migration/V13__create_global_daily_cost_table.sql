-- Global daily AI cost ledger for the cross-user cost guard (1.0.2 "kill-switch").
-- Per-user caps bound a single account; this table bounds the AGGREGATE daily spend
-- so a free-tier abuse wave, a pricing bug, or a viral spike can't run up an unbounded
-- OpenAI bill. One row per calendar day (Europe/Berlin, matching the usage clock),
-- incremented after each successful provider call.
CREATE TABLE global_daily_cost (
    period_day      VARCHAR(10) PRIMARY KEY,          -- ISO date, e.g. '2026-06-12'
    total_cost_eur  NUMERIC(14, 6) NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE global_daily_cost
    ADD CONSTRAINT chk_global_daily_cost_non_negative
        CHECK (total_cost_eur >= 0);
