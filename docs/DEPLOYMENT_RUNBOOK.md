# WatchMyAI Deployment Runbook

## Live Backend Checks

Run these after every deploy:

```bash
curl -i https://api.watchmyai.app/actuator/health
curl -i https://api.watchmyai.app/actuator/health/readiness
curl -i https://api.watchmyai.app/api/v1/plans
curl -i -H "Authorization: Bearer <release-session-token>" https://api.watchmyai.app/api/v1/app-store/status
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

WATCHMYAI_USD_TO_EUR=0.92
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
export RELEASE_TAG="$(date -u +%Y%m%d%H%M%S)-$(git rev-parse --short HEAD)"
docker build -t "watchmyai-backend:$RELEASE_TAG" .
docker stop watchmyai-backend || true
docker rm watchmyai-backend || true
docker run -d \
  --name watchmyai-backend \
  --restart unless-stopped \
  --env-file /opt/watchmyai/.env.production \
  -p 8080:8080 \
  "watchmyai-backend:$RELEASE_TAG"
```

Keep Caddy/Nginx in front of the app for HTTPS at `https://api.watchmyai.app`.

## Rollback

Keep the previous image tag before deploying a new one:

```bash
docker image ls watchmyai-backend
docker stop watchmyai-backend || true
docker rm watchmyai-backend || true
docker run -d \
  --name watchmyai-backend \
  --restart unless-stopped \
  --env-file /opt/watchmyai/.env.production \
  -p 8080:8080 \
  "watchmyai-backend:<previous-release-tag>"
```

Run `scripts/final-release-gates.sh` after rollback with `WATCHMYAI_RELEASE_BEARER_TOKEN` set.

## Database Backup and Restore Gate

Before every production deploy:

```bash
pg_dump "$DATABASE_URL" --format=custom --file "/opt/watchmyai/backups/watchmyai-$(date -u +%Y%m%d%H%M%S).dump"
```

Monthly, verify restore into a disposable database:

```bash
createdb watchmyai_restore_check
pg_restore --dbname watchmyai_restore_check /opt/watchmyai/backups/<backup-file>.dump
dropdb watchmyai_restore_check
```

Redis is used for rate-limit and notification-deduplication windows only. Do not treat Redis as authoritative subscription, user, or usage state.

## Alerting Minimum

Configure external checks or hosting alerts for:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- HTTP 5xx spike
- OpenAI 401/403/429 spike
- App Store notification verification failures
- Postgres backup failure
