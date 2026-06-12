# Monthly OpenAI Cost Reconciliation (QR-COST-01)

**Why:** the per-user cost caps protect us from a single runaway account, but they
only know about what we *recorded*. The OpenAI invoice is ground truth. Once a month
we compare our internal estimate (`ai_request_log.estimated_request_cost_eur`) to the
actual invoice so model-price changes, retry/refund leakage, or a wrong USD→EUR rate
surface early instead of quietly eating the margin.

## Run it

```bash
# Previous calendar month, prod DB from $DATABASE_URL (jdbc: prefix is stripped):
cd watchmyai-backend
./scripts/reconcile-ai-costs.sh

# Explicit month + DSN:
MONTH=2026-06 ./scripts/reconcile-ai-costs.sh "postgresql://user:pass@host:5432/watchmyai"
```

Keep `USD_TO_EUR` equal to `application.yaml` → `watchmyai.ai.pricing.usd-to-eur`
(default `0.92`); the report inverts it to show `est_cost_usd`, which is the figure
that lines up with the USD OpenAI invoice.

## Read it

- **Per model / Month total** — `est_cost_usd` is what to compare against the OpenAI
  invoice line items (per model) and the invoice total for the month.
- **Status breakdown** — `FAILED` rows with `input_tokens > 0` are calls that reached
  OpenAI, consumed tokens, then errored. We refund the user (cost recorded as 0), so
  this token spend is **not** in our `est_cost_*` totals — it's the retry/refund
  leakage. If it's material, add it to the expected invoice before comparing.
- **Per plan** — `avg_cost_eur_per_request` and totals vs. the plan cost caps
  (`FREE 0.10 €`, `PLUS 1.20 €`, `PRO 2.80 €` per user/month). A plan trending toward
  its cap means the limits/caps need a relook.

## Decision rule

1. `invoice_total_usd` vs. report `est_cost_usd` (month total) + estimated FAILED leakage.
2. **Within ±10%:** fine, record the numbers.
3. **Over +10% (we under-counted):** check (a) the model price table in
   `application.yaml` against OpenAI's current pricing page, (b) FAILED leakage volume,
   (c) the USD→EUR rate. Update `application.yaml` pricing and re-run.
4. Log the month's `invoice_usd`, `est_usd`, and `delta_%` somewhere durable so the
   trend is visible over time.

> First run: do it for the launch month as soon as the first OpenAI invoice is
> available — that's the cheapest moment to catch a pricing/rate mistake.
