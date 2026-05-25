# WatchMyAI Production Checklist

## Current Implementation Status

-   Backend stores verified subscription entitlement state in `app_store_subscription`.
-   iOS Settings exposes account deletion; the backend re-verifies Sign in with Apple, revokes Apple authorization, and removes account-associated service data.
-   `/api/v1/subscription/sync` verifies App Store transaction JWS when verification is enabled and stores the entitlement before updating the user plan.
-   `/api/v1/app-store/notifications` accepts App Store Server Notifications V2, verifies nested transaction data, and updates the user plan for renewal, expiration, refund, grace, billing retry, upgrade, and downgrade states.
-   `/api/v1/plans` exposes the server-side Plan/Quota catalog used by the iOS paywall to avoid client/server limit drift.
-   Production profile exposes health/readiness endpoints through Spring Boot Actuator.
-   `/api/v1/ai/ask`, AI polling, auth, subscription sync, and App Store notifications have Redis-backed production rate limits.
-   AI model routing, token pricing, FX rate, plan limits, and monthly cost caps are config-backed under `watchmyai.ai.*` and `watchmyai.plan-catalog`.
-   Debug plan manipulation remains disabled in `prod`.
-   Live deployment at `https://api.watchmyai.app` reports health `UP`.
-   Live App Store Server status reports `verificationEnabled=true`, `credentialsConfigured=true`, and `productionReady=true`.
-   Apple root certificates are bundled as classpath resources under `src/main/resources/apple/`.

## Required Environment

-   Production startup now validates the required secrets with `ProductionSecretsValidator`.
-   Use `.env.production.example` as the deployment template. Keep real values in the host secret manager only.
-   `SPRING_PROFILES_ACTIVE=prod`
-   `OPENAI_API_KEY`
-   `APPLE_CLIENT_ID=com.sasanrafatnami.WatchMyAI`
-   `APP_STORE_BUNDLE_ID=com.sasanrafatnami.WatchMyAI`
-   `APP_STORE_APP_APPLE_ID`
-   `APP_STORE_ISSUER_ID`
-   `APP_STORE_KEY_ID`
-   `APP_STORE_PRIVATE_KEY`
-   `APP_STORE_ENVIRONMENT=PRODUCTION` (TestFlight/Sandbox transactions are verified by the fallback verifier)
-   `APP_STORE_VERIFICATION_ENABLED=true` after App Store Server credentials are set
-   `WATCHMYAI_USD_TO_EUR` reviewed before release and whenever OpenAI billing currency assumptions change

## Production Startup Gate

With `SPRING_PROFILES_ACTIVE=prod`, the backend fails fast unless:

-   `OPENAI_API_KEY` is present and OpenAI mock mode is disabled.
-   `APPLE_CLIENT_ID` contains `com.sasanrafatnami.WatchMyAI`.
-   `APP_STORE_BUNDLE_ID` is `com.sasanrafatnami.WatchMyAI`.
-   `APP_STORE_APP_APPLE_ID` is a positive numeric App Store Connect app ID.
-   `APP_STORE_ISSUER_ID`, `APP_STORE_KEY_ID`, and the full `.p8` `APP_STORE_PRIVATE_KEY` are present.
-   `APP_STORE_ENVIRONMENT=PRODUCTION`; the live backend must not use Sandbox as its primary verifier.
-   `APP_STORE_VERIFICATION_ENABLED=true`.
-   Every configured AI model has matching pricing.

## App Store Server API

-   App Store Connect > Users and Access > Integrations > App Store Connect API:
    -   create/download In-App Purchase key
    -   set `APP_STORE_ISSUER_ID`
    -   set `APP_STORE_KEY_ID`
    -   set `APP_STORE_PRIVATE_KEY` with the full `.p8` content
-   App Store Connect > App Information:
    -   set `APP_STORE_APP_APPLE_ID`
    -   keep `APP_STORE_BUNDLE_ID=com.sasanrafatnami.WatchMyAI`
-   Verification:
    -   Production backend: `APP_STORE_ENVIRONMENT=PRODUCTION`
    -   TestFlight receipts still report `SANDBOX` and are accepted through the validated Sandbox fallback
    -   enable `APP_STORE_VERIFICATION_ENABLED=true`
    -   authenticated `/api/v1/app-store/status` must report `productionReady=true`

## App Store Connect

-   Create subscriptions:
    -   Plus: `watchmyai.plus.monthly`, `2,99 EUR / month`
    -   Pro: `watchmyai.pro.monthly`, `6,99 EUR / month`
    -   Rank Pro higher than Plus in the subscription group.
-   Add App Store Server Notifications V2 URL:
    -   `https://<api-domain>/api/v1/app-store/notifications`
-   Add privacy text for:
    -   Apple account identifier
    -   purchase/subscription status
    -   AI prompts and generated answers
-   Add Terms of Use and Privacy Policy URLs. The frontend currently points to:
    -   `https://api.watchmyai.app/privacy`
    -   `https://api.watchmyai.app/terms`

## Monitoring

-   Track HTTP status rate by endpoint:
    -   `/api/v1/ai/ask`
    -   `/api/v1/subscription/sync`
    -   `/api/v1/app-store/notifications`
-   Alert on:
    -   OpenAI provider 401/403
    -   OpenAI provider 429
    -   App Store verification failures
    -   backend 5xx spike
    -   AI job queue saturation
    -   monthly AI cost burn approaching plan caps
-   Every response includes `X-Request-Id`; include this ID in support/debug notes.
-   For VPS production, add external uptime checks for:
    -   `GET /actuator/health`
    -   `GET /actuator/health/readiness`

## Remaining Release Tasks

-   Run a TestFlight sandbox purchase for Plus and Pro, then Restore Purchases.
-   Confirm App Store Server Notifications arrive in backend logs and update `/api/v1/subscription/status`.
-   Confirm `/api/v1/subscription/sync` receives real `signedTransactionInfo` from StoreKit after purchase/restore.
-   Confirm `/api/v1/plans` matches paywall copy and final Free/Plus/Pro product limits before App Review.
-   Run a production backup and restore check before the first paid launch.
-   Confirm in-app account deletion with a TestFlight test account, including Apple reauthentication, local sign-out, and synced chat removal.
-   Prepare final App Store screenshots, app icon, privacy nutrition labels, Terms of Use, and Privacy Policy.
-   Before App Store release, verify `APP_STORE_ENVIRONMENT=PRODUCTION` and set the Production App Store Server Notification URL.

## Release Gates

-   iOS target builds on device.
-   Watch target builds as paired app.
-   Sign in with Apple succeeds on iOS.
-   Watch unlocks only after iOS sign-in.
-   `/api/v1/auth/status` returns `userType=apple`.
-   `/api/v1/subscription/status` reflects the active plan.
-   StoreKit purchase and restore both sync to backend.
-   App Store Server status reports `productionReady=true` with authenticated release gate token.
-   `/api/v1/plans` returns Free `5/day`, `20/month`, Plus `100/day`, `1000/month`, Pro `200/day`, `1500/month`.
-   Debug endpoints remain unavailable outside `dev`.
-   Failed requests include a `requestId` in JSON and `X-Request-Id` response header.
