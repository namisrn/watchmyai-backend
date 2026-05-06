# WatchMyAI Production Checklist

## Current Implementation Status

-   Backend stores verified subscription entitlement state in `app_store_subscription`.
-   `/api/v1/subscription/sync` verifies App Store transaction JWS when verification is enabled and stores the entitlement before updating the user plan.
-   `/api/v1/app-store/notifications` accepts App Store Server Notifications V2, verifies nested transaction data, and updates the user plan for renewal, expiration, refund, grace, billing retry, upgrade, and downgrade states.
-   Production profile exposes health/readiness endpoints through Spring Boot Actuator.
-   `/api/v1/ai/ask` has an in-memory per-user/per-IP rate limit for the first production pass.
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
-   `APP_STORE_ENVIRONMENT=SANDBOX` for TestFlight, `PRODUCTION` for release
-   `APP_STORE_VERIFICATION_ENABLED=true` after App Store Server credentials are set

## Production Startup Gate

With `SPRING_PROFILES_ACTIVE=prod`, the backend fails fast unless:

-   `OPENAI_API_KEY` is present and OpenAI mock mode is disabled.
-   `APPLE_CLIENT_ID` contains `com.sasanrafatnami.WatchMyAI`.
-   `APP_STORE_BUNDLE_ID` is `com.sasanrafatnami.WatchMyAI`.
-   `APP_STORE_APP_APPLE_ID` is a positive numeric App Store Connect app ID.
-   `APP_STORE_ISSUER_ID`, `APP_STORE_KEY_ID`, and the full `.p8` `APP_STORE_PRIVATE_KEY` are present.
-   `APP_STORE_ENVIRONMENT` is `SANDBOX` for TestFlight or `PRODUCTION` for release.
-   `APP_STORE_VERIFICATION_ENABLED=true`.

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
    -   Sandbox/TestFlight: `APP_STORE_ENVIRONMENT=SANDBOX`
    -   Release: `APP_STORE_ENVIRONMENT=PRODUCTION`
    -   enable `APP_STORE_VERIFICATION_ENABLED=true`
    -   `/api/v1/app-store/status` must report `productionReady=true`

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
    -   `https://watchmyai.app/privacy`
    -   `https://watchmyai.app/terms`

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
-   Every response includes `X-Request-Id`; include this ID in support/debug notes.
-   For VPS production, add external uptime checks for:
    -   `GET /actuator/health`
    -   `GET /actuator/health/readiness`

## Remaining Release Tasks

-   Run a TestFlight sandbox purchase for Plus and Pro, then Restore Purchases.
-   Confirm App Store Server Notifications arrive in backend logs and update `/api/v1/subscription/status`.
-   Confirm `/api/v1/subscription/sync` receives real `signedTransactionInfo` from StoreKit after purchase/restore.
-   Confirm rate limits match the final Free/Plus/Pro product limits before App Review.
-   Prepare final App Store screenshots, app icon, privacy nutrition labels, Terms of Use, and Privacy Policy.
-   Before App Store release, switch `APP_STORE_ENVIRONMENT=PRODUCTION` and set the Production App Store Server Notification URL.

## Release Gates

-   iOS target builds on device.
-   Watch target builds as paired app.
-   Sign in with Apple succeeds on iOS.
-   Watch unlocks only after iOS sign-in.
-   `/api/v1/auth/status` returns `userType=apple`.
-   `/api/v1/subscription/status` reflects the active plan.
-   StoreKit purchase and restore both sync to backend.
-   App Store Server status reports `productionReady=true`.
-   Debug endpoints remain unavailable outside `dev`.
-   Failed requests include a `requestId` in JSON and `X-Request-Id` response header.
