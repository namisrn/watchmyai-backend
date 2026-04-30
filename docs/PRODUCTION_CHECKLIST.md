# WatchMyAI Production Checklist

## Required Environment

- `OPENAI_API_KEY`
- `APPLE_CLIENT_ID=com.sasanrafatnami.WatchMyAI`
- `APP_STORE_BUNDLE_ID=com.sasanrafatnami.WatchMyAI`
- `APP_STORE_APP_APPLE_ID`
- `APP_STORE_ISSUER_ID`
- `APP_STORE_KEY_ID`
- `APP_STORE_PRIVATE_KEY`
- `APP_STORE_ENVIRONMENT=SANDBOX` for TestFlight, `PRODUCTION` for release
- `APP_STORE_VERIFICATION_ENABLED=true` after App Store Server credentials are set

## App Store Connect

- Create subscriptions:
  - `watchmyai.plus.monthly`
  - `watchmyai.pro.monthly`
- Add App Store Server Notifications V2 URL:
  - `https://<api-domain>/api/v1/app-store/notifications`
- Add privacy text for:
  - Apple account identifier
  - purchase/subscription status
  - AI prompts and generated answers

## Release Gates

- iOS target builds on device.
- Watch target builds as paired app.
- Sign in with Apple succeeds on iOS.
- Watch unlocks only after iOS sign-in.
- `/api/v1/auth/status` returns `userType=apple`.
- `/api/v1/subscription/status` reflects the active plan.
- StoreKit purchase and restore both sync to backend.
- App Store Server status reports `productionReady=true`.
- Debug endpoints remain unavailable outside `dev`.
