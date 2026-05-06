# WatchMyAI Deployment Runbook

## Live Backend Checks

Run these after every deploy:

```bash
curl -i https://api.watchmyai.app/actuator/health
curl -i https://api.watchmyai.app/actuator/health/readiness
curl -i https://api.watchmyai.app/api/v1/app-store/status
```

Expected App Store status for TestFlight:

```json
{
  "bundleId": "com.sasanrafatnami.WatchMyAI",
  "environment": "SANDBOX",
  "verificationEnabled": true,
  "credentialsConfigured": true,
  "productionReady": true
}
```

If `verificationEnabled` or `credentialsConfigured` is `false`, the VPS is missing App Store Server API secrets or is not running the latest image.

## Required VPS Environment

Set these in the server environment or Docker Compose secret/env section:

```env
SPRING_PROFILES_ACTIVE=prod

DATABASE_URL=jdbc:postgresql://<host>:5432/watchmyai
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>

OPENAI_API_KEY=<openai-api-key>
WATCHMYAI_OPENAI_MOCK_ENABLED=false

APPLE_CLIENT_ID=com.sasanrafatnami.WatchMyAI

APP_STORE_BUNDLE_ID=com.sasanrafatnami.WatchMyAI
APP_STORE_APP_APPLE_ID=<numeric-app-apple-id>
APP_STORE_ISSUER_ID=<app-store-connect-issuer-id>
APP_STORE_KEY_ID=<in-app-purchase-key-id>
APP_STORE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n<contents-of-downloaded-p8-key>\n-----END PRIVATE KEY-----"
APP_STORE_ENVIRONMENT=SANDBOX
APP_STORE_VERIFICATION_ENABLED=true
```

Use `APP_STORE_ENVIRONMENT=SANDBOX` for TestFlight. Switch to `PRODUCTION` only for App Store release.

## App Store Connect Notification URL

Set App Store Server Notifications V2 to:

```text
https://api.watchmyai.app/api/v1/app-store/notifications
```

Set the Sandbox URL first. Add the Production URL before App Store release.

## Docker Deploy Sketch

```bash
docker build -t watchmyai-backend:latest .
docker stop watchmyai-backend || true
docker rm watchmyai-backend || true
docker run -d \
  --name watchmyai-backend \
  --restart unless-stopped \
  --env-file /opt/watchmyai/.env.production \
  -p 8080:8080 \
  watchmyai-backend:latest
```

Keep Caddy/Nginx in front of the app for HTTPS at `https://api.watchmyai.app`.
