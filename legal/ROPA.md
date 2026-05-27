# Verzeichnis von Verarbeitungstätigkeiten (ROPA)

> **Art. 30 DSGVO** — Pflicht für jeden Verantwortlichen, unabhängig von der
> Größe des Unternehmens, sobald die Verarbeitung
> a) nicht nur gelegentlich erfolgt **oder**
> b) besondere Datenkategorien betrifft **oder**
> c) Risiko für Rechte/Freiheiten Betroffener birgt.
>
> Alle drei Bedingungen treffen auf WatchMyAI zu → ROPA ist Pflicht.
>
> Diese Datei muss bei Anfrage der Aufsichtsbehörde (in DE: Landesdatenschutzbeauftragte
> oder BfDI) **binnen 7 Tagen** vorgelegt werden können.

---

## 0. Verantwortlicher

| Feld | Wert |
|---|---|
| Verantwortlicher i.S.d. Art. 4 Nr. 7 DSGVO | <<Vollständiger Name>>, <<Anschrift>> (siehe `IMPRESSUM.md`) |
| Datenschutzbeauftragter | **Nicht erforderlich** nach § 38 BDSG (Solo-Unternehmer, keine 20+ MA, keine systematische umfangreiche Beobachtung im Sinne Art. 37 Abs. 1 lit. b/c DSGVO) |
| Vertreter in der EU | Entfällt (Verantwortlicher sitzt selbst in der EU) |
| Aufsichtsbehörde | <<Landesdatenschutzbehörde des Bundeslands der Anschrift — z.B. „Der Bayerische Landesbeauftragte für den Datenschutz">> |
| Kontakt für Betroffenenanfragen | support@watchmyai.app |

---

## 1. Verarbeitungstätigkeit: User-Authentifizierung (Sign in with Apple)

| Feld | Wert |
|---|---|
| **Zweck** | Eindeutige Identifikation des Nutzers, Schutz vor Account-Übernahme, Verknüpfung mit Subscription-Entitlements |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung — Bereitstellung der App ist ohne Account technisch nicht möglich) |
| **Kategorien Betroffener** | Nutzer der WatchMyAI-App |
| **Kategorien personenbezogener Daten** | • Apple-User-ID (`sub`-Claim des Apple-JWT)<br>• Optional: E-Mail-Adresse (echt oder Apple-Private-Relay)<br>• Internes Session-Token (zufälliger 256-bit String)<br>• Zeitstempel: `createdAt`, `updatedAt`, `lastSeenAt` |
| **Speicherort** | PostgreSQL bei Hetzner (Deutschland) — Tabellen `app_user`, `user_session` |
| **Empfänger** | Apple Inc. (Identitätsprüfung beim Sign-In via JWT-Verifikation) |
| **Drittlandtransfer** | Keine aktive Übermittlung an Apple — Apple bestätigt nur die Identität, der Datenfluss läuft Client → Apple → unser Backend. Apple selbst hat DPA + SCCs. |
| **Speicherfrist** | • Apple-User-ID + Email: bis Account-Löschung durch Nutzer (Art. 17 DSGVO)<br>• Session-Tokens: 30 Tage rolling (sliding renewal), automatischer Purge nach Ablauf |
| **Technische und organisatorische Maßnahmen (TOM)** | • Token im iOS-Keychain mit `AfterFirstUnlockThisDeviceOnly`<br>• Backend-Token nur als Hash gespeichert<br>• TLS 1.3 für gesamte Kommunikation<br>• Apple-JWS-Signaturprüfung gegen JWKS-Cache mit 24h-TTL<br>• Rate-Limit 10 Sign-In-Versuche pro IP/Minute |

---

## 2. Verarbeitungstätigkeit: KI-Anfrage-Verarbeitung

| Feld | Wert |
|---|---|
| **Zweck** | Kerndienstleistung: Erzeugung einer KI-Antwort auf die Nutzeranfrage |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung) |
| **Kategorien Betroffener** | Nutzer der WatchMyAI-App (Free/Plus/Pro) |
| **Kategorien personenbezogener Daten** | • Prompt-Inhalt (kann freier Text mit potenziell personenbezogenen Inhalten sein, z.B. Frage „Wie schreibe ich einen Brief an Anna Müller?")<br>• Sprach-Code (z.B. `de-DE`)<br>• Quota-Metadaten: User-ID, Plan, Tagesnutzung, Monatsnutzung<br>• Generierte Antwort |
| **Speicherort** | • Prompt: nur transient im RAM des Backends + im OpenAI-Request<br>• Antwort + Metadaten: PostgreSQL `ai_request_log` bei Hetzner (DE) |
| **Empfänger** | OpenAI, L.L.C., USA — als Auftragsverarbeiter |
| **Drittlandtransfer** | **JA** — OpenAI sitzt in den USA. Absicherung durch:<br>• Standard Contractual Clauses (SCCs) Modul 2 im OpenAI-DPA<br>• Transfer Impact Assessment (TIA) — siehe `legal/signed/TIA_OpenAI_<<datum>>.pdf`<br>• Zero Data Retention bei OpenAI beantragt (siehe `AVV_CHECKLIST.md`) — Prompts werden bei OpenAI nicht über die Antwortgenerierung hinaus gespeichert<br>• Keine Apple-User-ID, kein Email, keine Geräte-IDs im OpenAI-Request |
| **Speicherfrist** | • Prompt-Inhalt: 0 Sekunden bei uns (nie gespeichert, nur transient verarbeitet)<br>• KI-Antwort: 30 Tage in `ai_request_log.answer`, danach automatischer Purge durch Spring `@Scheduled` Job (siehe `AiRequestLogRetentionJob`)<br>• Kosten- und Nutzungsmetadaten: 24 Monate (Rechnungslegungs- und Steuerpflicht nach § 147 AO), danach Anonymisierung |
| **TOM** | • TLS 1.3 zu OpenAI<br>• API-Key in Hetzner-Secrets, nicht im Repo<br>• Pre-Submit-Filter im Backend: Prompt > 2000 Zeichen wird abgelehnt (begrenzt versehentlichen Massendaten-Upload)<br>• Quoten-Cap (`monthlyCostCapEur`) verhindert ausufernde Kosten<br>• Idempotency-Key verhindert Duplikat-Verarbeitung |

---

## 3. Verarbeitungstätigkeit: Subscription-Management

| Feld | Wert |
|---|---|
| **Zweck** | Plan-Entitlement-Prüfung (Free/Plus/Pro), Renewal-Tracking, Refund-Handling |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung — kostenpflichtige Tarife) |
| **Kategorien Betroffener** | Zahlende Nutzer (Plus / Pro) |
| **Kategorien personenbezogener Daten** | • Apple-Transaktions-ID<br>• Apple-Original-Transaktions-ID<br>• Produkt-ID (z.B. `watchmyai.plus.monthly`)<br>• Ablaufdatum<br>• `appAccountToken` (UUID, von uns generiert)<br>• Apple-S2S-Notifications-Payload (JSON, enthält keine Email/Name) |
| **Speicherort** | PostgreSQL `app_store_subscription` + `app_store_notification_log` bei Hetzner (DE) |
| **Empfänger** | Apple Inc. (StoreKit + App Store Server Notifications) |
| **Drittlandtransfer** | Apple verarbeitet als Auftragsverarbeiter unter EU-DPA. Push der S2S-Notifications geht Apple → unser Backend, keine aktive Übermittlung. |
| **Speicherfrist** | Bis Account-Löschung, mindestens aber 10 Jahre Aufbewahrung der Belege (§ 147 AO) für Steuerprüfungen. Nach Account-Löschung bleibt nur eine anonymisierte Buchungszeile (ohne Apple-IDs) erhalten. |
| **TOM** | • JWS-Signaturprüfung der Apple-Notifications<br>• Atomare Quoten-Reservierung (SQL `INSERT … ON CONFLICT`)<br>• Idempotenz via Notification-UUID<br>• Apple Sign-In Webhook für Consent-Revoke (siehe `AppleSignInNotificationController`) |

---

## 4. Verarbeitungstätigkeit: Quota- und Kostenverfolgung

| Feld | Wert |
|---|---|
| **Zweck** | Durchsetzung der Tageslimits, Monatslimits, Kostencaps |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung) + Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse: Schutz vor Kostenüberlauf, Fair-Use) |
| **Kategorien Betroffener** | Alle Nutzer |
| **Kategorien personenbezogener Daten** | • User-ID, Plan-Typ<br>• `usedDailyRequests`, `usedMonthlyRequests`, `usedPremiumRequests`<br>• `estimatedMonthlyCostEur`<br>• Periodenkennzeichen (`YYYY-MM`, `YYYY-MM-DD`) |
| **Speicherort** | PostgreSQL `user_usage` bei Hetzner (DE) |
| **Empfänger** | Keine externen Empfänger |
| **Drittlandtransfer** | Nein |
| **Speicherfrist** | • Aktuelle Periode: solange Account aktiv<br>• Historische Perioden: 24 Monate (Kostenanalyse, Streitfälle), danach Anonymisierung |
| **TOM** | Wie #1 (gemeinsame Infrastruktur) |

---

## 5. Verarbeitungstätigkeit: Chat-Historie auf Endgerät + iCloud-Sync

| Feld | Wert |
|---|---|
| **Zweck** | Chat-Verlauf für Folgefragen und Wiederverwendung am gleichen oder anderen Apple-Gerät desselben Nutzers |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung — Chat-Persistenz ist Kernfunktion) |
| **Kategorien Betroffener** | Nutzer der WatchMyAI-App |
| **Kategorien personenbezogener Daten** | • User-Prompts (Klartext, kann personenbezogen sein)<br>• KI-Antworten<br>• Zeitstempel<br>• Conversation-Titel |
| **Speicherort** | • SwiftData lokal auf iPhone / Apple Watch<br>• Optional bei eingeschaltetem iCloud: Apple iCloud (CloudKit Container `iCloud.com.sasanrafatnami.WatchMyAI`)<br>• **Niemals auf unserem Backend** |
| **Empfänger** | Apple Inc. (nur bei aktivem iCloud-Sync, Ende-zu-Ende-verschlüsselt durch Apple-Standard) |
| **Drittlandtransfer** | iCloud-Daten können nach Apple-Konfiguration in USA gespeichert werden; bei aktiviertem „Erweiterten Datenschutz" (ADP) jedoch Ende-zu-Ende-verschlüsselt mit User-Key — Apple kann nicht entschlüsseln |
| **Speicherfrist** | Bis Nutzer die App deinstalliert oder Chats manuell löscht. Backend-seitig: nicht vorhanden. |
| **TOM** | • SwiftData mit on-device-Verschlüsselung (Apple-Standard)<br>• iCloud-Sync respektiert Apple ADP wenn vom User aktiviert<br>• Account-Löschung im Backend hat keinen Einfluss auf lokale Chats (User muss App deinstallieren) |

---

## 6. Verarbeitungstätigkeit: AI-Anfrage-Logging zur Kostenkontrolle (DPIA-relevant)

| Feld | Wert |
|---|---|
| **Zweck** | Kostenanalyse, Quota-Reconciliation gegen OpenAI-Rechnung, Fehleranalyse bei Reklamationen |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse — Schutz vor wirtschaftlichem Schaden), abgewogen gegen die Interessen der Betroffenen durch Datenminimierung und kurze Aufbewahrung |
| **Kategorien Betroffener** | Alle Nutzer mit aktiven AI-Anfragen |
| **Kategorien personenbezogener Daten** | • User-ID<br>• Generierte Antwort (kann theoretisch User-Prompt-Reste enthalten)<br>• Mode, Model, Tokens, Cost<br>• Quoten-Snapshot |
| **Speicherort** | PostgreSQL `ai_request_log` bei Hetzner (DE) |
| **Empfänger** | Keine externen Empfänger |
| **Drittlandtransfer** | Nein |
| **Speicherfrist** | • `answer`-Spalte: **30 Tage**, danach NULL via Retention-Job<br>• Aggregierte Metadaten (Tokens, Cost, Model): 24 Monate, danach Anonymisierung |
| **TOM** | • DB-Verschlüsselung at-rest (Hetzner-Standard für verschlüsselte Volumes)<br>• Zugriff nur über produktive Anwendung mit Service-Account<br>• Kein Mitarbeiter-Direktzugriff in der Solo-Phase; vor Team-Wachstum: rollenbasierte Zugriffsbeschränkung definieren |

---

## 7. Verarbeitungstätigkeit: Account-Löschung (Art. 17 DSGVO)

| Feld | Wert |
|---|---|
| **Zweck** | Erfüllung des „Rechts auf Vergessenwerden" |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. c DSGVO (rechtliche Verpflichtung) |
| **Kategorien Betroffener** | Alle Nutzer, die ihre Löschung anfordern |
| **Kategorien personenbezogener Daten** | Alle oben genannten (außer aus steuerlichen Aufbewahrungspflichten erhaltene Buchungszeilen) |
| **Speicherort** | n/a (Löschung statt Speicherung) |
| **Empfänger** | Apple Inc. — separater Revoke-Call an Apple-`/auth/revoke` (siehe `AppleSignInTokenRevocationService`) |
| **Drittlandtransfer** | Nein |
| **Speicherfrist** | n/a |
| **TOM** | • In-App-Button „Delete Account" mit Apple-Reauth-Bestätigung<br>• Apple-Sign-In Webhook für `consent-revoked` / `account-delete` (server-initiated, bspw. wenn User die App in iCloud-Settings widerruft)<br>• Transaktionale Löschung: aiRequestLog, appStoreSubscription, userUsage, userPlan, userSession, appUser<br>• Steuerrelevante Buchungszeilen bleiben anonymisiert für 10 Jahre nach § 147 AO erhalten |

---

## 8. Verarbeitungstätigkeit: Produkt-Telemetrie (falls aktiviert)

> Wird ergänzt, sobald die Telemetrie-Pipeline deployed ist. Aktuell N/A.

---

## 9. Datenflussdiagramm

```
┌──────────────┐        TLS 1.3       ┌─────────────────┐
│ iOS / Watch  │ ─────── Apple JWT ──▶│  appleid.apple  │
│  App         │ ◀────── User ID ─────│  .com (Verify)  │
│              │                       └─────────────────┘
│              │
│              │        TLS 1.3       ┌─────────────────┐
│              │ ───── Bearer Token ─▶│  WatchMyAI BE   │
│              │ ◀───── AI Answer ────│  Hetzner DE     │
│              │                       │  Postgres+Redis │
│              │ ◀───── iCloud Sync ──┤                 │
└──────────────┘        (E2E, optional)└─────────┬───────┘
                                                  │ TLS 1.3
                                                  ▼
                                       ┌──────────────────┐
                                       │  OpenAI API USA  │
                                       │  (SCCs+ZDR)      │
                                       └──────────────────┘
                                                  ▲
                                                  │ App-Store
                                                  │ Server-to-
                                                  │ Server
                                                  ▼
                                       ┌──────────────────┐
                                       │  Apple App Store │
                                       │  Server          │
                                       └──────────────────┘
```

**Daten die GAR NICHT erhoben werden:**

- Klarnamen (außer User gibt sie freiwillig im Prompt ein)
- Standortdaten
- Kontakte
- Browsing-/Tracking-Daten
- Gerätekennungen über die Apple-Account-ID hinaus
- Biometrische Daten
- Werbe-IDs (App nutzt `NSPrivacyTrackingDomains` = leer, `NSPrivacyTracking` = false)

---

## 10. Pflicht zur Aktualisierung

Dieses Verzeichnis ist nach Art. 30 Abs. 4 DSGVO **bei jeder substanziellen
Änderung der Verarbeitung zu aktualisieren** — insbesondere:

- Neuer Subprozessor → Eintrag in 1-7 ergänzen, `AVV_CHECKLIST.md` synchron
- Neue Datenkategorie → neue Tätigkeit aufnehmen
- Änderung der Speicherfrist → Eintrag aktualisieren + Retention-Job anpassen
- Neue Rechtsgrundlage → Eintrag und Privacy Policy aktualisieren

Bei jeder Änderung Datum unten erhöhen.

---

**Stand:** <<Datum bei Inbetriebnahme + bei jeder Änderung>>
**Verantwortlich:** Siehe `IMPRESSUM.md`
**Aufsichtsbehörde-Anfrage:** Diese Datei + `AVV_CHECKLIST.md` + `DPIA.md` als ZIP bereitstellen
