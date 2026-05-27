# Telemetrie-Taxonomie

> Was wir messen — und was wir **niemals** messen. Diese Datei ist die kanonische
> Quelle für jeden neuen Telemetrie-Event. Vor Code-Änderung Datei updaten,
> sonst wird der Server-side-Filter (`TelemetryService`) den Event ablehnen.

---

## 0. Goldene Regeln

1. **Keine Inhalte.** Prompts, Antworten, Mail-Adressen, Klarnamen, Tokens — nie als Event-Property.
2. **Snake-Case ASCII**, max. 64 Zeichen, validiert serverseitig (`^[a-z][a-z0-9_]{0,63}$`).
3. **Aggregierbarkeit > Reichhaltigkeit.** Ein Event mit 3 sauberen Properties ist mehr wert als eins mit 15 unscharfen.
4. **PostHog-kompatibel.** Naming-Konvention (snake_case, vergangenheits-orientiert) damit ein späteres PostHog-Adapter ohne Renaming gemappt werden kann.

---

## 1. Eventliste — aktuell instrumentiert

| Event-Name | Auslöser | Platform | Properties | Zweck im Funnel |
|---|---|---|---|---|
| `quota_blocked` | Backend lehnt AI-Request wegen Quota ab | `backend` | `throttle_state`, `remaining_requests`, `monthly_usage_percent` | Wo bleiben User hängen? Daily vs. Monthly vs. Cost-Cap |
| `ai_answer_completed` | OpenAI-Antwort erfolgreich an User geliefert | `backend` | `model`, `mode`, `input_tokens`, `output_tokens`, `used_premium_model` | Aktivierung + tägliche Engagement-Metrik |
| `subscription_state_changed` | Apple-S2S oder Client-Sync hat Subscription verändert | `backend` | `product_id`, `verification_source`, `notification_type`, `notification_subtype`, `active`, `billing_retry`, `grace_period` | Conversion, Renewal, Refund, Cancel — alles über `notification_type` differenziert |
| `paywall_viewed` | User öffnet die Paywall-Seite (`IOSPaywallScreen.onAppear`) | `ios` | `source` (`plan_screen` / `quota_nudge` / `limit_reached` …) | Funnel-Top: wie viele kommen überhaupt zur Kasse? |
| `quota_nudge_shown` | 70%-Banner erscheint im iOS Home | `ios` | `usage_percent` | Mid-Funnel: welche User sehen die Soft-Upsell-Stelle? |

---

## 2. Geplante Events — nicht implementiert

| Event-Name | Wann | Property-Idee |
|---|---|---|
| `purchase_initiated` | User tappt CTA im Paywall, vor StoreKit-Sheet | `product_id`, `source` |
| `restore_invoked` | User tappt "Restore Purchases" | `source` |
| `signin_completed` | Apple-Sign-In erfolgreich | `is_first_time` |
| `app_first_launch` | Erster Start nach Install | `locale` |
| `conversation_started` | Erste Nachricht in einem neuen Chat | `source` (`watch` / `iphone`) |

---

## 3. Was wir **nie** in Properties erlauben

Server-side Blocklist (`TelemetryService.FORBIDDEN_PROPERTY_KEYS`) und Client-side
Mirror (`Telemetry.forbiddenPropertyKeys`). Wer einen dieser Keys verwendet,
bekommt das ganze Event verworfen mit WARN-Log:

```
prompt        answer        input         content       message       text
email         name          phone         address       passwort      password
token         secret        apikey        api_key
userid        user_id       appleuserid   apple_user_id
ip            ipaddress     ip_address
```

**Ergänzung** der Liste in `TelemetryService.java` UND `Telemetry.swift` gemeinsam.
Mismatch zwischen den beiden Seiten würde ein subtle data leak ermöglichen.

---

## 4. User-Identification

| Schicht | Wer kennt die User-ID? | Warum |
|---|---|---|
| Apple Sign-In | Apple-Subject (echte ID) | Auth |
| Backend Storage (app_user) | Apple-Subject + `userId = "apple:..."` | Account-Lifecycle |
| **Telemetrie** | **`user_id_hash`** = SHA-256(userId)[0..16] hex | Kohortenanalyse ohne Re-Identifikation |

Joins über `user_id_hash` möglich, aber nur innerhalb der Telemetrie-Tabelle.
Es gibt keinen Mapping-Table zurück zur echten `userId` — Re-Identifizierung
würde Brute-Force über alle möglichen Apple-Subjects erfordern (impraktikabel,
Schutz nicht absolut aber DSGVO-konform datasparsam).

---

## 5. Property-Wert-Typen

Backend akzeptiert beliebige JSON-Werte. Frontend ist auf vier Primitive
beschränkt (`TelemetryPropertyValue`):

| Swift-Konstruktor | Beispiel | Verwendung |
|---|---|---|
| `.value("plus")` (String) | Plan-Namen, Mode, Source | Häufigster Fall |
| `.value(42)` (Int) | Counts, Percent | Numerisch |
| `.value(3.14)` (Double) | Latenzen, Quoten-Ratios | Selten |
| `.value(true)` (Bool) | Flags | Conditional Pfade |

**String-Werte werden serverseitig nach 256 Zeichen abgeschnitten** —
defensive Sicherung gegen versehentliche Datenlecks (z. B. wenn jemand eine
Fehler-Message in eine Property steckt, die zufällig persönliche Daten enthält).

---

## 6. Aufbewahrungsdauer

- **Ungefilterte Roh-Events:** 12 Monate (Konfig im `application.yaml`,
  künftiger `TelemetryRetentionJob` analog `AiRequestLogRetentionJob`).
- **Aggregierte Metriken** (per SQL extrahiert nach `docs/TELEMETRY_DASHBOARDS.md`):
  unbegrenzt — sind dann anonyme Statistik, nicht mehr personenbezogen.

Auf Account-Löschung (Art. 17 DSGVO): `TelemetryEventRepository.deleteByUserIdHash(...)`
wird vom `AccountDeletionService` aufgerufen (zu integrieren — derzeit nicht).

---

## 7. Wann KEIN Telemetrie-Event sinnvoll ist

- Bei Streaming oder schnellen Tastatur-Inputs → würde Buffer überfluten.
- Bei jeder Tap-Geste auf einem Knopf → zu fein-granular, unbrauchbar im Funnel.
- Für Performance-Latenzen → besser Micrometer/Actuator (gibts schon).
- Für Fehler-Stack-Traces → besser Sentry (nicht deployed).

**Faustregel:** Wenn du dem Event als „Funnel-Schritt" oder „Conversion-Phase"
einen Namen geben kannst → tracken. Sonst nicht.

---

## 8. Pflegezyklus

| Trigger | Aktion |
|---|---|
| Neuer Event hinzugefügt | Tabelle § 1 ergänzen, Property-Keys begründen |
| Neue Forbidden-Key-Variante | § 3 ergänzen, beide Code-Stellen synchron |
| Event-Definition geändert (Properties) | Old-Event abandonen, NEUER Event-Name mit Suffix `_v2` — alte Daten bleiben für historische Trends erhalten |
| Retention-Frist geändert | ROPA §8 + Privacy Policy §10 anpassen |

---

**Stand:** Telemetrie initial-deploy.
**Verantwortlich:** Siehe `legal/IMPRESSUM.md`
