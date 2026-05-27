# Auftragsverarbeitungsverträge (AVV) — Checkliste

> Nach **Art. 28 DSGVO** muss jeder Auftragsverarbeiter (Processor) per
> schriftlichem Vertrag verpflichtet werden. Das gilt für **jeden** Dienst, der
> personenbezogene Daten im Auftrag von WatchMyAI verarbeitet.
>
> **Ein einziger fehlender AVV ist eine DSGVO-Verletzung — Bußgeld bis zu
> 10 Mio. € oder 2 % des Jahresumsatzes (Art. 83 Abs. 4 DSGVO).**
>
> Diese Datei führt alle Subprozessoren mit Status auf. Vor EU-Launch muss
> jeder Eintrag **Status: ✅ abgeschlossen** haben.

---

## Status-Übersicht

| # | Subprozessor | Verarbeitete Daten | Drittland? | AVV-Status | Letzte Prüfung |
|---|---|---|---|---|---|
| 1 | **Apple Inc.** (Sign in with Apple, App Store, IAP, APNs, iCloud) | Apple-User-ID, Email (privat-relay), Subscription-Status, Chat-Sync via CloudKit | USA (DPF-zertifiziert + SCCs) | <<❌ TODO / ✅ abgeschlossen am …>> | <<Datum>> |
| 2 | **OpenAI, L.L.C.** (Responses API: KI-Antworten) | Prompts (Inhalt der User-Frage), generierte Antworten, technische Metadaten | USA (**nicht** DPF-zertifiziert seit 2024 — SCCs zwingend) | <<❌ TODO / ✅ abgeschlossen am …>> | <<Datum>> |
| 3 | **Hetzner Online GmbH** (Server, Postgres, Redis, Object Storage) | Alle gespeicherten Daten in deinem Server-Image | Deutschland (kein Drittland) | <<❌ TODO / ✅ abgeschlossen am …>> | <<Datum>> |
| 4 | **PostHog Inc.** ODER **Plausible Insights OÜ** (Telemetrie, falls deployed) | Aggregierte Funnel-Events (kein Prompt-Inhalt) | PostHog: USA + EU-Region wählbar / Plausible: EE-EU | <<❌ TODO falls deployed / N/A>> | <<Datum>> |
| 5 | **Sentry, GmbH** (Error-Tracking, falls deployed) | Stacktraces, Error-Kontexte (können personenbezogene Felder enthalten) | EU-Region wählbar (Sentry SaaS Frankfurt) | <<❌ TODO falls deployed / N/A>> | <<Datum>> |

---

## Detail-Anweisungen pro Subprozessor

### 1. Apple Inc.

**Was du tun musst:**

1. App Store Connect → **Agreements, Tax, and Banking** → den **Paid Apps Agreement** akzeptieren.
   Damit gilt automatisch die **Apple Data Processing Addendum** als Teil des Apple Developer Program License Agreement.
2. Bei Konzern-Anbietern in der EU zusätzlich: **EU Data Processing Addendum** explizit signieren
   (Apple stellt das auf Anfrage über `dpo@apple.com` zur Verfügung).

**Quelle:** https://www.apple.com/legal/transparency/

**Verarbeitete personenbezogene Daten in WatchMyAI:**

- Apple-User-ID (`sub` aus Apple-JWT)
- Optional: Email-Adresse (echt oder privat-relay)
- Subscription-Transaktions-IDs
- iCloud-Sync-Inhalt (Chats) — End-to-End-verschlüsselt durch Apple, wir können nicht lesen

**Schutzmaßnahmen / Apple's Pflichten laut DPA:**

- Verarbeitung nur auf unsere Weisung
- Datensicherheit nach Art. 32 DSGVO
- Unterstützungspflicht bei Anfragen Betroffener
- Subprozessoren werden gelistet (Apple Subprocessor List)

---

### 2. OpenAI, L.L.C.

**Was du tun musst:**

1. Im OpenAI Dashboard → **Settings → Organization → Data Controls** → **Data Processing Addendum (DPA)** öffnen
2. Online-DPA elektronisch unterzeichnen (Self-Service seit 2024)
3. **Zero Data Retention (ZDR)** beantragen — Email an `privacy@openai.com` mit Begründung
   („Wir verarbeiten Nutzeranfragen in der EU/EWR, brauchen ZDR um Speicherfrist auf 0 Tage zu reduzieren und Drittlandtransfer zu minimieren")
4. **Achtung Drittland (USA):**
   - OpenAI ist seit 2024 **nicht mehr unter dem EU-US Data Privacy Framework zertifiziert** (Status alle 12 Monate prüfen)
   - **Standard Contractual Clauses (SCCs)** sind im OpenAI-DPA enthalten — beim Unterzeichnen prüfen ob Modul 2 (Controller → Processor) gewählt
   - **Transfer Impact Assessment (TIA)** als interne Notiz erstellen (Vorlage: https://noyb.eu/sites/default/files/2024-04/TIA_template.pdf)

**Quelle:** https://openai.com/policies/data-processing-addendum

**Verarbeitete personenbezogene Daten in WatchMyAI:**

- Prompt-Inhalt (kann personenbezogen sein — vom User eingegebene Frage)
- Generierte Antwort
- Technisches Metadaten (Token-Counts, Modell, Latenz)

**KEINE Daten** die wir an OpenAI senden:
- Apple-User-ID (wir hashen nicht mal das, da kein Bezug nötig)
- Email-Adresse
- Geräte-Identifikatoren

**Verifizierung im Code:**

```
# Suche im Codebase ob versehentlich personenbezogene Daten in den OpenAI-Body landen
grep -rn "userId\|appleSubject\|email\|appAccountToken" \
  watchmyai-backend/src/main/java/com/watchmyai/ai/
```

---

### 3. Hetzner Online GmbH

**Was du tun musst:**

1. Hetzner-Konto → **Sicherheit** → **AVV (Auftragsverarbeitungsvertrag)**
2. Online-AVV elektronisch akzeptieren (Self-Service)
3. PDF-Bestätigung in dieses Verzeichnis als `legal/signed/Hetzner_AVV_<<datum>>.pdf` ablegen

**Quelle:** https://docs.hetzner.com/general/general-terms-and-conditions/data-privacy-faq/

**Kein Drittlandtransfer:** Hetzner-Rechenzentren in Deutschland (Falkenstein, Nürnberg) und Finnland (Helsinki). Wenn du nur DE-Lokationen nutzt → strenger EU-Schutz, kein TIA nötig.

**Verifizierung der Server-Location:**

```bash
# Hetzner Cloud Console → dein Server → "Standort" muss "Deutschland" oder "Finnland" sein
# In application-prod.yaml prüfen ob keine US-Region konfiguriert ist
```

---

### 4. PostHog / Plausible (nur falls Telemetrie deployed)

**Bei PostHog self-hosted auf deinem Hetzner-Server:** kein AVV nötig (kein Subprozessor — alles bleibt bei Hetzner).

**Bei PostHog Cloud:**

1. PostHog → **Settings → Privacy & Security → Data Processing Agreement** signieren
2. **EU-Region** wählen (Frankfurt) statt US
3. **SCCs** falls US-Region (vermeiden!)

**Bei Plausible Cloud:**

1. Plausible → **Settings → Subscriptions** → automatisch AVV bei Sign-Up
2. Sitz Estland, EU-Recht → kein Drittland, kein TIA

---

### 5. Sentry (nur falls Error-Tracking deployed)

**Bei Sentry SaaS:**

1. Sentry → **Settings → Legal & Compliance → Data Processing Addendum** signieren
2. **Frankfurt-Region** wählen (sentry.io/region/eu)
3. Source-Maps und User-IDs in Sentry-SDK-Config auf Hashes statt Klartext setzen

---

## Verifizierung vor jedem Release

Vor jedem App-Store-Submission als CI-Gate oder manuelles Pre-Release-Checklist:

```
[ ] Alle ✅ in der Status-Tabelle oben
[ ] Alle signierten AVVs als PDF in legal/signed/ abgelegt
[ ] Subprocessor-Liste in PRIVACY_POLICY.md synchron mit dieser Datei
[ ] Bei OpenAI: ZDR-Status alle 6 Monate verifizieren (Email-Reply behalten)
[ ] Bei Apple/Sentry/PostHog: DPF-Status auf Trustverifier-Portal prüfen
    → https://www.dataprivacyframework.gov/list
```

---

## Bei Änderung eines Subprozessors

Nach Art. 28 Abs. 2 DSGVO musst du Nutzer über Änderungen informieren, wenn du
deine Subprozessor-Liste änderst:

1. Diese Datei und `PRIVACY_POLICY.md` aktualisieren
2. In-App: bei nächstem Start dezenter Hinweis „Wir haben unsere
   Datenschutzerklärung aktualisiert" mit Link
3. Bestandsnutzer per Email informieren (falls Email-Adresse vorhanden — bei
   Apple-Sign-In-Relay-Adresse über Apple-Relay-Service)
4. Mindestens **30 Tage Frist** vor dem Wechsel (DSGVO-Best-Practice)

---

**Stand:** <<Datum bei jeder Änderung aktualisieren>>
**Verantwortlich:** Siehe `IMPRESSUM.md`
