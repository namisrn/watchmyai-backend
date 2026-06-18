# WatchMyAI Backend – Codex Handoff

## Project Goal

WatchMyAI is an Apple Watch AI assistant with optional iOS companion app.
The backend is a Spring Boot AI Gateway.

Main product goals:
- No OpenAI API key in the client
- Free/Plus/Pro plan catalog served by the backend
- Plus/Pro subscriptions via StoreKit 2 and App Store Server verification
- Monthly usage and estimated AI costs tracked per user
- Automatic quota/cost throttling
- Short AI responses with strict token limits
- Cheap models by default
- Premium request quota reserved for Pro
- OpenAI requests sent with `store=false` and without user/account identifiers
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
`develop`

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

The endpoint is idempotent by `(userId, clientRequestId)` and uses an async job
flow for AI generation. A duplicate request returns the existing job/response and
must not charge quota or call OpenAI again.

Polling:
- `GET /api/v1/ai/ask/{clientRequestId}`

The client-facing API contract is unchanged for 1.0.1.

### Quota / Usage

Usage is persisted in PostgreSQL via:
- `UserUsageEntity`
- `UserUsageRepository`
- `UsageService`

Flyway migration:
- usage and AI request log migrations are under `src/main/resources/db/migration`

Canonical plan limits live in `application.yaml` `watchmyai.plan-catalog`:
- Free: `5/day`, `20/month`
- Plus: `60/day`, `500/month`
- Pro: `150/day`, `1000/month`, `60` premium requests

### User Context

Current user is abstracted through:
- `UserIdentity`
- `UserContextService`
- `DevelopmentUserContextService`
- production bearer/session and Apple auth services

Development can still use the dev/test identity path, but production user context
is driven by authenticated sessions and App Store/Apple account state.

### Debug Endpoints

Quota debug endpoints are only active in `dev` profile:
- `/api/v1/quota/debug`
- `/api/v1/quota/debug/plan/{planType}`
- `/api/v1/quota/debug/reset`
- `/api/v1/quota/debug/cost/high`

## Release Stabilization Notes

- 1.0.1 is a StoreKit/quota/copy stability release, not a Siri/App Shortcuts release.
- New iOS/watchOS AppIntent source files are intentionally parked for v1.1.
- Existing widget and deep-link behavior remains unchanged.
- Backend CI should run `./gradlew check jacocoTestReport --no-daemon`.
- Frontend CI should run `swift test` plus isolated iOS and watchOS Xcode builds.

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
