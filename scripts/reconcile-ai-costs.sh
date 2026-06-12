#!/usr/bin/env bash
# Monthly OpenAI cost reconciliation runner (QR-COST-01).
#
# Runs scripts/reconcile-ai-costs.sql against the production database and prints a
# per-model / per-status / per-plan cost summary to compare against the OpenAI invoice.
#
# Connection (first match wins):
#   1. $1                      a libpq DSN or URL passed as the first argument
#   2. $WATCHMYAI_DB_DSN       explicit libpq DSN/URL
#   3. $DATABASE_URL           the app's prod var; a leading 'jdbc:' is stripped so
#                              'jdbc:postgresql://…' becomes a psql-usable URL
#   4. libpq env (PGHOST/PGUSER/PGDATABASE/…) if none of the above is set
#
# Month defaults to the PREVIOUS calendar month (the one you just got invoiced for).
# Override: MONTH=2026-06 ./scripts/reconcile-ai-costs.sh
# Rate defaults to 0.92; keep it equal to application.yaml ai.pricing.usd-to-eur.
#
#   ./scripts/reconcile-ai-costs.sh                      # prev month, $DATABASE_URL
#   MONTH=2026-06 ./scripts/reconcile-ai-costs.sh "$DSN"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/reconcile-ai-costs.sql"

USD_TO_EUR="${USD_TO_EUR:-0.92}"

# Previous calendar month, portably across GNU and BSD/macOS date.
if [[ -n "${MONTH:-}" ]]; then
  month="$MONTH"
elif date -v-1m +%Y-%m >/dev/null 2>&1; then
  month="$(date -v-1m +%Y-%m)"            # BSD/macOS
else
  month="$(date -d 'last month' +%Y-%m)"  # GNU/Linux
fi

# Half-open [start, end) boundary for the reported month.
start="${month}-01"
if date -v+1m +%Y-%m >/dev/null 2>&1; then
  end="$(date -j -v+1m -f '%Y-%m-%d' "$start" +%Y-%m-%d)"   # BSD/macOS
else
  end="$(date -d "$start +1 month" +%Y-%m-%d)"              # GNU/Linux
fi

# Resolve the connection string.
dsn="${1:-${WATCHMYAI_DB_DSN:-${DATABASE_URL:-}}}"
dsn="${dsn#jdbc:}"   # psql accepts postgresql://… but not jdbc:postgresql://…

if ! command -v psql >/dev/null 2>&1; then
  echo "FATAL: psql not found on PATH." >&2
  exit 2
fi

echo "Reconciling AI provider cost for ${month} [${start}, ${end}) (usd_to_eur=${USD_TO_EUR})"
if [[ -n "$dsn" ]]; then
  exec psql "$dsn" -v ON_ERROR_STOP=1 -v "start=${start}" -v "end=${end}" -v "usd_to_eur=${USD_TO_EUR}" -f "$SQL_FILE"
else
  # No explicit DSN — rely on standard libpq PG* env vars.
  exec psql -v ON_ERROR_STOP=1 -v "start=${start}" -v "end=${end}" -v "usd_to_eur=${USD_TO_EUR}" -f "$SQL_FILE"
fi
