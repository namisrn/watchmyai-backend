# Telemetrie-Dashboards (SQL-Templates)

> Direkt gegen `psql` auf der Produktion-DB ausführbar. Alle Queries verwenden
> nur `telemetry_event` + die existierenden Plan-/User-Tabellen. Kein PostHog,
> kein Grafana, keine extra Infrastruktur.
>
> Speichere dir die wichtigsten Queries als psql-`\set` oder als Saved-Snippets
> in deinem SQL-Client.
>
> **Frische der Daten:** ~ Realtime (Events werden direkt eingefügt).
> **Frische des iOS-Funnels:** bis zu 30 Sekunden Latenz (Client-Buffer-Flush).

---

## 1. Aktive Nutzer (DAU / MAU)

**DAU letzte 7 Tage:**

```sql
SELECT
    DATE(occurred_at) AS day,
    COUNT(DISTINCT user_id_hash) AS dau
FROM telemetry_event
WHERE event_name = 'ai_answer_completed'
  AND occurred_at >= NOW() - INTERVAL '7 days'
  AND user_id_hash IS NOT NULL
GROUP BY DATE(occurred_at)
ORDER BY day DESC;
```

**MAU rolling-30:**

```sql
SELECT COUNT(DISTINCT user_id_hash) AS mau
FROM telemetry_event
WHERE event_name = 'ai_answer_completed'
  AND occurred_at >= NOW() - INTERVAL '30 days'
  AND user_id_hash IS NOT NULL;
```

**DAU/MAU-Ratio** (Stickiness — gut wenn > 0.2):

```sql
WITH dau AS (
    SELECT COUNT(DISTINCT user_id_hash) AS n
    FROM telemetry_event
    WHERE event_name = 'ai_answer_completed'
      AND occurred_at >= NOW() - INTERVAL '1 day'
      AND user_id_hash IS NOT NULL
), mau AS (
    SELECT COUNT(DISTINCT user_id_hash) AS n
    FROM telemetry_event
    WHERE event_name = 'ai_answer_completed'
      AND occurred_at >= NOW() - INTERVAL '30 days'
      AND user_id_hash IS NOT NULL
)
SELECT
    dau.n AS dau,
    mau.n AS mau,
    ROUND(dau.n::numeric / NULLIF(mau.n, 0), 3) AS ratio
FROM dau, mau;
```

---

## 2. Funnel: Aktivierung (Sign-In → erste KI-Antwort)

Erste KI-Antwort pro User (Cohort = Tag der Aktivierung):

```sql
SELECT
    DATE(MIN(occurred_at)) AS activation_day,
    COUNT(DISTINCT user_id_hash) AS activated_users
FROM telemetry_event
WHERE event_name = 'ai_answer_completed'
  AND user_id_hash IS NOT NULL
GROUP BY user_id_hash
ORDER BY activation_day DESC
LIMIT 100;
```

**Median time-to-first-answer:** TODO sobald `signin_completed` Event implementiert ist (s. Taxonomie § 2).

---

## 3. Paywall-Funnel (Conversion-Kernmetrik)

**Paywall-Views der letzten 7 Tage nach aktuellem Plan:**

```sql
SELECT
    DATE(occurred_at) AS day,
    plan,
    COUNT(*) AS views
FROM telemetry_event
WHERE event_name = 'paywall_viewed'
  AND occurred_at >= NOW() - INTERVAL '7 days'
GROUP BY DATE(occurred_at), plan
ORDER BY day DESC, plan;
```

**Paywall-View → Purchase Conversion** (letzte 30 Tage):

```sql
WITH paywall_users AS (
    SELECT DISTINCT user_id_hash
    FROM telemetry_event
    WHERE event_name = 'paywall_viewed'
      AND occurred_at >= NOW() - INTERVAL '30 days'
      AND user_id_hash IS NOT NULL
), converted AS (
    SELECT DISTINCT user_id_hash
    FROM telemetry_event
    WHERE event_name = 'subscription_state_changed'
      AND (properties_json LIKE '%"active":true%')
      AND (properties_json LIKE '%SUBSCRIBED%' OR properties_json LIKE '%DID_RENEW%' OR properties_json LIKE '%verification_source%')
      AND occurred_at >= NOW() - INTERVAL '30 days'
      AND user_id_hash IS NOT NULL
)
SELECT
    (SELECT COUNT(*) FROM paywall_users) AS paywall_viewers,
    (SELECT COUNT(*) FROM paywall_users p WHERE p.user_id_hash IN (SELECT user_id_hash FROM converted)) AS converters,
    ROUND(
        (SELECT COUNT(*) FROM paywall_users p WHERE p.user_id_hash IN (SELECT user_id_hash FROM converted))::numeric
        / NULLIF((SELECT COUNT(*) FROM paywall_users), 0),
        4
    ) AS conversion_ratio;
```

---

## 4. Plan-Mix in Realtime

Aktive zahlende Nutzer aus den letzten 7 Tagen (nutzt `ai_answer_completed` als
„User ist im Plan aktiv"-Signal):

```sql
SELECT
    plan,
    COUNT(DISTINCT user_id_hash) AS active_users
FROM telemetry_event
WHERE event_name = 'ai_answer_completed'
  AND occurred_at >= NOW() - INTERVAL '7 days'
  AND user_id_hash IS NOT NULL
GROUP BY plan
ORDER BY active_users DESC;
```

---

## 5. Quota-Blocked-Analyse (Wo brechen User ab?)

**Wer hat in den letzten 7 Tagen die Quota-Wall getroffen?**

```sql
SELECT
    plan,
    COUNT(DISTINCT user_id_hash) AS users_blocked,
    COUNT(*) AS total_blocks
FROM telemetry_event
WHERE event_name = 'quota_blocked'
  AND occurred_at >= NOW() - INTERVAL '7 days'
GROUP BY plan
ORDER BY users_blocked DESC;
```

**Wie verteilt sich `throttle_state`?** (daily / monthly / cost-cap):

```sql
SELECT
    plan,
    (properties_json::json ->> 'throttle_state') AS throttle_state,
    COUNT(*) AS occurrences
FROM telemetry_event
WHERE event_name = 'quota_blocked'
  AND occurred_at >= NOW() - INTERVAL '30 days'
GROUP BY plan, throttle_state
ORDER BY occurrences DESC;
```

---

## 6. Quota-Nudge-Funnel (70%-Nudge → Paywall)

Wie viele User sehen den Nudge und tappen dann tatsächlich auf "Manage plan"?

```sql
WITH nudge_seen AS (
    SELECT DISTINCT user_id_hash,
           DATE(occurred_at) AS day
    FROM telemetry_event
    WHERE event_name = 'quota_nudge_shown'
      AND occurred_at >= NOW() - INTERVAL '14 days'
      AND user_id_hash IS NOT NULL
), paywall_after_nudge AS (
    SELECT n.user_id_hash, n.day
    FROM nudge_seen n
    INNER JOIN telemetry_event p
        ON p.user_id_hash = n.user_id_hash
        AND p.event_name = 'paywall_viewed'
        AND DATE(p.occurred_at) = n.day
)
SELECT
    (SELECT COUNT(*) FROM nudge_seen) AS nudge_seen,
    (SELECT COUNT(DISTINCT user_id_hash) FROM paywall_after_nudge) AS nudge_to_paywall,
    ROUND(
        (SELECT COUNT(DISTINCT user_id_hash) FROM paywall_after_nudge)::numeric
        / NULLIF((SELECT COUNT(*) FROM nudge_seen), 0), 4
    ) AS conversion_ratio;
```

---

## 7. Subscription-Event-Timeline pro User

Diagnostisch — wenn ein einzelner Nutzer Support anschreibt:

```sql
-- Ersetze 'abc123...' durch SHA-256(userId, "apple:...", 16 Byte hex)
SELECT
    occurred_at,
    event_name,
    plan,
    properties_json
FROM telemetry_event
WHERE user_id_hash = 'abc123...'
ORDER BY occurred_at DESC
LIMIT 50;
```

**User-Hash aus User-ID berechnen** (für deinen Support-Workflow):

```sql
-- Postgres-Plugin pgcrypto vorausgesetzt (ist in Hetzner-Postgres-Default an)
SELECT SUBSTRING(ENCODE(DIGEST('apple:000123.abc456', 'sha256'), 'hex'), 1, 32);
```

---

## 8. Modell-/Kosten-Mix für Forecast

Welcher OpenAI-Tarif wird wie genutzt?

```sql
SELECT
    DATE_TRUNC('day', occurred_at) AS day,
    (properties_json::json ->> 'model') AS model,
    plan,
    COUNT(*) AS calls,
    SUM((properties_json::json ->> 'input_tokens')::int) AS input_tokens,
    SUM((properties_json::json ->> 'output_tokens')::int) AS output_tokens
FROM telemetry_event
WHERE event_name = 'ai_answer_completed'
  AND occurred_at >= NOW() - INTERVAL '7 days'
GROUP BY day, model, plan
ORDER BY day DESC, calls DESC;
```

---

## 9. Performance-Sanity-Checks

**Event-Volumen pro Tag** (wenn massiv anders als gestern → Bug oder Outage):

```sql
SELECT DATE(occurred_at) AS day, COUNT(*) AS events
FROM telemetry_event
WHERE occurred_at >= NOW() - INTERVAL '14 days'
GROUP BY DATE(occurred_at)
ORDER BY day DESC;
```

**Tabellengröße:** (für Capacity-Planning, prüf einmal/Monat)

```sql
SELECT pg_size_pretty(pg_total_relation_size('telemetry_event'));
```

---

## 10. Maintenance-Queries

**Retention manuell** (bis der `TelemetryRetentionJob` deployed ist):

```sql
DELETE FROM telemetry_event
WHERE occurred_at < NOW() - INTERVAL '12 months';
```

**Account-Löschung manuell** (sollte über `AccountDeletionService` laufen,
aber als Fallback):

```sql
DELETE FROM telemetry_event WHERE user_id_hash = '<<hash hier>>';
```

---

**Pflege:** Bei neuen Events in `TELEMETRY_TAXONOMY.md` ergänzen → entsprechenden
SQL-Snippet hier hinzufügen. Anti-Pattern: Queries direkt im Chat oder in Notes
behalten — die landen dann nicht im Repo und gehen verloren.
