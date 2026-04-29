# WatchMyAI Backend – Codex Handoff

## Project Goal

WatchMyAI is an Apple Watch AI assistant with optional iOS companion app.
The backend is a Spring Boot AI Gateway.

Main product goals:
- No OpenAI API key in the client
- Free plan with hard lifetime limit
- Plus/Pro subscriptions later via StoreKit 2
- Monthly usage and estimated AI costs tracked per user
- Automatic quota/cost throttling
- Short AI responses with strict token limits
- Cheap models by default
- Expensive models only limited for Pro
- Privacy and security from the beginning

## Current Backend Stack

- Java 21
- Spring Boot 4.0.6
- Gradle
- PostgreSQL
- Flyway
- Spring Data JPA
- Docker Compose for local PostgreSQL
- Dev profile as default

## Current Branch

Current active branch:
`feature/request-idempotency`

Base branch:
`develop`

## Existing Architecture

### AI Endpoint

`POST /api/v1/ai/ask`

Request:
- input
- source: watch | ios
- mode: short_answer | translate | rewrite | explain | premium_reasoning
- language: de | en | auto
- clientRequestId

### Quota / Usage

Usage is persisted in PostgreSQL via:
- `UserUsageEntity`
- `UserUsageRepository`
- `UsageService`

Flyway migration:
- `V1__create_user_usage_table.sql`

### User Context

Current user is abstracted through:
- `UserIdentity`
- `UserContextService`
- `DevelopmentUserContextService`

Currently returns:
`debug-user`

Later this should be replaced by real auth/session context.

### Debug Endpoints

Quota debug endpoints are only active in `dev` profile:
- `/api/v1/quota/debug`
- `/api/v1/quota/debug/plan/{planType}`
- `/api/v1/quota/debug/reset`
- `/api/v1/quota/debug/cost/high`

## Current Request Idempotency Work

Goal:
Use `clientRequestId` to prevent duplicate AI requests from being charged/counting usage multiple times.

Current files:
- `AiRequestLogEntity`
- `AiRequestLogRepository`
- `V2__create_ai_request_log_table.sql`
- `AiService` partially integrated with request log lookup

Desired behavior:
1. Get current user ID from `UserContextService`
2. Check `AiRequestLogRepository.findByUserIdAndClientRequestId(userId, clientRequestId)`
3. If found, return stored `AskAIResponse`
4. If not found:
    - check quota
    - call OpenAI
    - estimate cost
    - record usage
    - store response in `ai_request_log`
    - return response

Important:
Duplicate request must not call OpenAI again.
Duplicate request must not call `UsageService.recordRequest(...)` again.

## Current Known Issues / Warnings

IntelliJ may show:
- Cannot resolve table `ai_request_log`
- Cannot resolve column ...

This is usually only because Flyway migration V2 has not been applied locally or IntelliJ DB was not synchronized.

Fix:
```bash
docker compose down -v
docker compose up -d
./gradlew clean build
./gradlew bootRun