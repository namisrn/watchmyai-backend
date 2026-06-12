-- Monthly OpenAI cost reconciliation (QR-COST-01).
--
-- Aggregates the internally-recorded provider cost from `ai_request_log` so it can
-- be compared against the actual OpenAI invoice for the same month. Run it via
-- scripts/reconcile-ai-costs.sh (which sets :start, :end and :usd_to_eur), or directly:
--
--   psql "$DSN" -v start=2026-06-01 -v end=2026-07-01 -v usd_to_eur=0.92 \
--        -f scripts/reconcile-ai-costs.sql
--
-- :start/:end  half-open [start, end) month boundary as 'YYYY-MM-DD'
-- :usd_to_eur  same rate as application.yaml `watchmyai.ai.pricing.usd-to-eur`
--              (cost_eur = cost_usd * rate  =>  cost_usd = cost_eur / rate)
--
-- Only COMPLETED requests carry a provider cost (BLOCKED never calls OpenAI; FAILED
-- is refunded to cost 0). FAILED rows are still surfaced separately because a call
-- that errored *after* consuming tokens is real provider spend we did NOT capture —
-- that gap is exactly the retry/refund leakage the profitability analysis warned of.

\echo '== AI cost reconciliation for [' :'start' ',' :'end' ') =='
\echo ''
\echo '-- Per model (COMPLETED requests only = billable provider calls) --'
SELECT
    COALESCE(model_used, '(none)')                AS model,
    COUNT(*)                                      AS requests,
    SUM(input_tokens)                             AS input_tokens,
    SUM(output_tokens)                            AS output_tokens,
    ROUND(SUM(estimated_request_cost_eur), 4)     AS est_cost_eur,
    ROUND(SUM(estimated_request_cost_eur) / :usd_to_eur, 4) AS est_cost_usd
FROM ai_request_log
WHERE status = 'COMPLETED'
  AND request_allowed = true
  AND created_at >= :'start'::timestamptz
  AND created_at <  :'end'::timestamptz
GROUP BY model_used
ORDER BY est_cost_eur DESC NULLS LAST;

\echo ''
\echo '-- Month total (COMPLETED) — compare est_cost_usd to the OpenAI invoice --'
SELECT
    COUNT(*)                                      AS requests,
    SUM(input_tokens)                             AS input_tokens,
    SUM(output_tokens)                            AS output_tokens,
    ROUND(SUM(estimated_request_cost_eur), 4)     AS est_cost_eur,
    ROUND(SUM(estimated_request_cost_eur) / :usd_to_eur, 4) AS est_cost_usd
FROM ai_request_log
WHERE status = 'COMPLETED'
  AND request_allowed = true
  AND created_at >= :'start'::timestamptz
  AND created_at <  :'end'::timestamptz;

\echo ''
\echo '-- Status breakdown — FAILED with tokens > 0 indicates uncaptured provider spend --'
SELECT
    status,
    COUNT(*)                                      AS rows,
    SUM(input_tokens)                             AS input_tokens,
    SUM(output_tokens)                            AS output_tokens,
    ROUND(SUM(estimated_request_cost_eur), 4)     AS est_cost_eur
FROM ai_request_log
WHERE created_at >= :'start'::timestamptz
  AND created_at <  :'end'::timestamptz
GROUP BY status
ORDER BY rows DESC;

\echo ''
\echo '-- Per plan (COMPLETED) — sanity-check spend against the monthly cost caps --'
SELECT
    plan_type,
    COUNT(*)                                      AS requests,
    ROUND(SUM(estimated_request_cost_eur), 4)     AS est_cost_eur,
    ROUND(AVG(estimated_request_cost_eur), 6)     AS avg_cost_eur_per_request
FROM ai_request_log
WHERE status = 'COMPLETED'
  AND request_allowed = true
  AND created_at >= :'start'::timestamptz
  AND created_at <  :'end'::timestamptz
GROUP BY plan_type
ORDER BY est_cost_eur DESC NULLS LAST;
