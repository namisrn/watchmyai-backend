#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${WATCHMYAI_API_BASE_URL:-https://api.watchmyai.app}"
EXPECTED_APP_STORE_ENVIRONMENT="${EXPECTED_APP_STORE_ENVIRONMENT:-PRODUCTION}"
WATCHMYAI_RELEASE_BEARER_TOKEN="${WATCHMYAI_RELEASE_BEARER_TOKEN:-}"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

require_status() {
  local method="$1"
  local path="$2"
  local expected_status="$3"
  local expected_content_type="${4:-}"
  local response_file
  local status
  local content_type

  response_file="$(mktemp)"
  status="$(
    curl -sS -X "$method" \
      -D "$response_file.headers" \
      -o "$response_file" \
      -w '%{http_code}' \
      "$API_BASE_URL$path"
  )"

  if [[ "$status" != "$expected_status" ]]; then
    printf 'Response body for %s %s:\n' "$method" "$path" >&2
    cat "$response_file" >&2
    fail "$method $path returned $status, expected $expected_status"
  fi

  if [[ -n "$expected_content_type" ]]; then
    content_type="$(awk 'BEGIN{IGNORECASE=1} /^content-type:/ {print $0}' "$response_file.headers")"
    if [[ "$content_type" != *"$expected_content_type"* ]]; then
      fail "$method $path content type was '$content_type', expected '$expected_content_type'"
    fi
  fi

  rm -f "$response_file" "$response_file.headers"
}

json_field() {
  local json="$1"
  local field="$2"

  python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get(sys.argv[2], ""))' "$json" "$field"
}

require_status GET /actuator/health 200 'application/vnd.spring-boot.actuator'
require_status GET /actuator/health/readiness 200 'application/vnd.spring-boot.actuator'
require_status GET /api/v1/auth/status 401 'application/json'
require_status GET /api/v1/plans 200 'application/json'
require_status GET /privacy 200 'text/html'
require_status GET /terms 200 'text/html'

if [[ -z "$WATCHMYAI_RELEASE_BEARER_TOKEN" ]]; then
  fail 'WATCHMYAI_RELEASE_BEARER_TOKEN must be set for protected /api/v1/app-store/status release gate'
fi

app_store_status="$(
  curl -sS \
    -H "Authorization: Bearer $WATCHMYAI_RELEASE_BEARER_TOKEN" \
    "$API_BASE_URL/api/v1/app-store/status"
)"
environment="$(json_field "$app_store_status" environment)"
verification_enabled="$(json_field "$app_store_status" verificationEnabled)"
credentials_configured="$(json_field "$app_store_status" credentialsConfigured)"
production_ready="$(json_field "$app_store_status" productionReady)"

if [[ "$environment" != "$EXPECTED_APP_STORE_ENVIRONMENT" ]]; then
  fail "/api/v1/app-store/status environment was '$environment', expected '$EXPECTED_APP_STORE_ENVIRONMENT'"
fi

if [[ "$verification_enabled" != "True" && "$verification_enabled" != "true" ]]; then
  fail '/api/v1/app-store/status verificationEnabled must be true'
fi

if [[ "$credentials_configured" != "True" && "$credentials_configured" != "true" ]]; then
  fail '/api/v1/app-store/status credentialsConfigured must be true'
fi

if [[ "$production_ready" != "True" && "$production_ready" != "true" ]]; then
  fail '/api/v1/app-store/status productionReady must be true'
fi

printf 'All final release gates passed for %s\n' "$API_BASE_URL"
