# Datenschutzerklärung — WatchMyAI

> Diese Datenschutzerklärung erfüllt die Informationspflichten nach **Art. 13
> DSGVO** und § 5 TMG für deutsche Nutzer. Sie wird in der App und über die
> URL `https://api.watchmyai.app/privacy` bereitgestellt.
>
> **Letzte Aktualisierung:** <<Datum bei Veröffentlichung>>
> **Sprachen:** Deutsch (verbindlich), Englisch (Übersetzung — siehe unten)

---

## 1. Verantwortlicher

Verantwortlicher i.S.d. Art. 4 Nr. 7 DSGVO ist:

```
<<Vollständiger Name>>
<<Straße und Hausnummer>>
<<PLZ und Ort>>
<<Land>>

E-Mail: support@watchmyai.app
```

Eine Verpflichtung zur Benennung eines Datenschutzbeauftragten nach § 38 BDSG
besteht nicht (Solo-Unternehmer, keine systematische umfangreiche Beobachtung
oder besondere Datenkategorien im Sinne von Art. 37 Abs. 1 lit. b/c DSGVO).

---

## 2. Wofür wir deine Daten verarbeiten — kurz

| Zweck | Was wir brauchen | Rechtsgrundlage |
|---|---|---|
| Account erstellen und einloggen | Apple-User-ID (über Sign in with Apple) | Vertrag (Art. 6 Abs. 1 lit. b) |
| KI-Antworten generieren | Deine eingegebene Frage | Vertrag (Art. 6 Abs. 1 lit. b) |
| Plus/Pro-Abo verwalten | Apple-Transaktions-ID | Vertrag (Art. 6 Abs. 1 lit. b) |
| Tages- und Monatslimits durchsetzen | Anzahl deiner Anfragen | Vertrag + berechtigtes Interesse (lit. b/f) |
| Kostenanalyse und Reklamationsbearbeitung | Gespeicherte Antworten 30 Tage lang | berechtigtes Interesse (lit. f) |
| Account löschen wenn du willst | n/a (Löschung statt Speicherung) | Rechtspflicht (lit. c) |

**Wir sammeln NICHT:** Standort, Kontakte, Browser-Verlauf, Werbe-IDs,
Tracking-Daten, biometrische Daten, Profildaten zu Marketingzwecken.

---

## 3. Sign in with Apple

Wenn du die App startest, ist eine Anmeldung über Sign in with Apple
erforderlich. Apple übermittelt uns dabei:

- Deine **Apple-User-ID** (ein langer Identifier, der nur für uns gilt)
- Eine **E-Mail-Adresse** — entweder deine echte oder eine Apple-Private-Relay-
  Adresse, je nach deiner Wahl
- **Name** wird nicht angefordert

Wir speichern die Apple-User-ID in unserer Datenbank und nutzen sie ausschließlich
zur Wiedererkennung deines Accounts. Die E-Mail nutzen wir nur, falls du uns
direkt kontaktierst (Support-Antworten).

**Rechtsgrundlage:** Vertragserfüllung (Art. 6 Abs. 1 lit. b DSGVO).
**Speicherdauer:** Bis zur Löschung deines Accounts.

---

## 4. KI-Anfragen — wer sieht deine Fragen?

### Datenfluss

```
Du tippst eine Frage  →  WatchMyAI-Server (Hetzner, Deutschland)
                       →  OpenAI Responses API (USA)
                       →  Antwort zurück
```

### Was passiert wo

| Stelle | Verarbeitet | Speichert |
|---|---|---|
| **Deine Watch / dein iPhone** | Den eingegebenen Text | Lokal in SwiftData (optional via iCloud synchronisiert) |
| **Unser Backend (Hetzner DE)** | Empfängt Frage, leitet weiter, empfängt Antwort | **Fragetext: 0 Sekunden** (nicht gespeichert)<br>Antworttext: **30 Tage** (für Kostenanalyse + Reklamationsbearbeitung), danach automatisch gelöscht |
| **OpenAI USA** | Verarbeitet Frage, generiert Antwort | **0 Sekunden** bei aktivem „Zero Data Retention"-Opt-In (von uns eingerichtet) |

### Drittlandtransfer USA — wichtig

OpenAI verarbeitet deine Frage in den USA. Die USA gelten nach EU-Recht als
**unsicheres Drittland** im Sinne des Art. 44 DSGVO.

**Welche Schutzmaßnahmen wir getroffen haben:**

- **Standard Contractual Clauses** (SCCs, EU-Kommission 2021/914) im Vertrag
  mit OpenAI signiert (Modul 2: Verantwortlicher → Auftragsverarbeiter)
- **Zero Data Retention** bei OpenAI aktiviert — OpenAI speichert deine Frage
  nicht über die Antwort hinaus
- **Datenminimierung:** Wir senden an OpenAI **keine** Apple-User-ID, **keine**
  E-Mail-Adresse, **keine** Geräte-IDs — nur den reinen Text deiner Frage
- **Transfer Impact Assessment** (TIA) intern dokumentiert

**Was du tun kannst, falls du US-Verarbeitung vermeiden willst:**

Die App ist ohne KI-Verarbeitung nicht nutzbar. Falls du grundsätzlich keine
US-Verarbeitung wünschst, kannst du die App nicht verwenden und solltest sie
deinstallieren. Eine alternative EU-Modellroute ist derzeit nicht verfügbar
(in Planung für 2026/27).

### Sensible Inhalte

**Wichtig:** Gib in der App **keine sensiblen Daten** ein wie:

- Passwörter, PINs, TANs, API-Keys
- Gesundheitsdaten, Diagnosen, Medikamentenpläne
- Bank-Daten, Kreditkartennummern
- Personenbezogene Daten Dritter (Namen, Adressen, Telefonnummern fremder Personen)
- Geschäftsgeheimnisse, vertrauliche Geschäftsdaten

WatchMyAI ist **kein** zertifiziertes Werkzeug für besondere Datenkategorien
nach Art. 9 DSGVO. Bei Verwendung sensibler Daten in Prompts trägst du das
Risiko selbst.

**Rechtsgrundlage:** Vertragserfüllung (Art. 6 Abs. 1 lit. b DSGVO).

---

## 5. Subscription-Verwaltung

Wenn du Plus oder Pro abonnierst, verarbeiten wir folgende Daten von Apple
über die App Store Server API:

- Produkt-ID (z.B. `watchmyai.plus.monthly`)
- Transaktions-ID und Original-Transaktions-ID
- Ablaufdatum und Renewal-Status
- Eine von uns generierte interne UUID (`appAccountToken`)

**Wir erhalten von Apple KEINE** Zahlungsdaten (Kreditkarte, IBAN, PayPal o.ä.) —
die Zahlung wickelt ausschließlich Apple ab. Wir bekommen nur die Information
„hat Plus aktiv" oder „hat Pro aktiv".

**Rechtsgrundlage:** Vertragserfüllung (Art. 6 Abs. 1 lit. b DSGVO).
**Speicherdauer:** Bis zur Account-Löschung, danach 10 Jahre für steuerliche
Aufbewahrung (anonymisiert nach § 147 AO).

---

## 6. Chat-Historie und iCloud

Deine Chats werden **lokal auf deinem iPhone und deiner Apple Watch**
gespeichert (SwiftData). Wenn du iCloud aktiviert hast, synchronisiert Apple
deine Chats zwischen deinen Geräten — das passiert direkt zwischen deinen
Geräten und Apples iCloud, **nicht** über unser Backend.

Bei aktivem Apple „Erweiterten Datenschutz" sind iCloud-Daten Ende-zu-Ende-
verschlüsselt — selbst Apple kann nicht mitlesen.

**Wir haben keinen Zugriff auf deine Chat-Historie.** Eine Account-Löschung
löscht **keine** Chats auf deinem Gerät — dazu musst du die App deinstallieren
oder Chats manuell in der App löschen.

---

## 7. Quota- und Kostenkontrolle

Wir zählen serverseitig:

- Anzahl deiner Anfragen heute / diesen Monat
- Geschätzte API-Kosten pro Monat

Diese Daten dienen ausschließlich dem Schutz vor Kostenüberlauf und der
fairen Nutzung. Wir erstellen damit **keine Profile** und werten **keine
Inhalte** aus.

**Rechtsgrundlage:** Vertragserfüllung + berechtigtes Interesse
(Art. 6 Abs. 1 lit. b und lit. f DSGVO).
**Speicherdauer:** Aktuelle Periode bis Account-Löschung, historische Perioden
24 Monate, dann anonymisiert.

---

## 8. KI-Antwort-Log

Server-seitig speichern wir den generierten Antworttext für **30 Tage** in der
Tabelle `ai_request_log` zusammen mit:

- Plan-Typ (Free/Plus/Pro)
- Verwendetes Modell
- Token-Anzahl und geschätzte Kosten

**Zweck:** Reklamationsbearbeitung („Diese Antwort war fehlerhaft"),
Kosten-Reconciliation gegen die OpenAI-Rechnung, Fehleranalyse bei
Service-Störungen.

**Nach 30 Tagen** wird das Antwortfeld automatisch geleert (NULL); die
aggregierten Metadaten (Tokens, Kosten) bleiben **anonym** weitere 24 Monate
zur internen Kostenanalyse erhalten.

**Rechtsgrundlage:** Berechtigtes Interesse (Art. 6 Abs. 1 lit. f DSGVO),
abgewogen gegen deine Interessen durch kurze Aufbewahrungsdauer und
Datenminimierung.

---

## 9. Empfänger und Subprozessoren

Folgende Auftragsverarbeiter setzen wir nach Art. 28 DSGVO ein:

| Anbieter | Zweck | Sitz | Rechtsrahmen |
|---|---|---|---|
| **Apple Inc.** | Sign in with Apple, App Store, IAP, APNs, iCloud-Sync | USA / EU-Niederlassung | DPA + SCCs + DPF-Zertifizierung |
| **OpenAI, L.L.C.** | KI-Antwort-Generierung | USA | DPA + SCCs (Modul 2), nicht DPF-zertifiziert |
| **Hetzner Online GmbH** | Server, Datenbank, Backups | Deutschland | AVV, kein Drittland |

Weitere Auftragsverarbeiter werden nur eingesetzt, nachdem diese Erklärung
aktualisiert wurde. Substanzielle Änderungen werden in der App mit einem
Hinweis beim nächsten Start kommuniziert.

---

## 10. Speicherfristen — Übersicht

| Datenart | Speicherdauer | Grund |
|---|---|---|
| Apple-User-ID, E-Mail | Bis Account-Löschung | Account-Identifikation |
| Session-Token | 30 Tage rolling | Sicherheits-Best-Practice |
| Prompt-Inhalt im Backend | **0 Sekunden** | Nicht gespeichert |
| Prompt-Inhalt bei OpenAI | **0 Sekunden** mit ZDR | OpenAI-Konfiguration |
| KI-Antwort | **30 Tage**, dann NULL | Reklamationsbearbeitung |
| Quoten-Counter | Aktuelle Periode + 24 Monate | Kostenanalyse |
| Transaktions-IDs | Bis Account-Löschung + 10 Jahre anonymisiert | § 147 AO |
| Lokale Chat-Historie | Bis du sie löschst oder App deinstallierst | Funktionsumfang |

---

## 11. Deine Rechte als betroffene Person

Nach Art. 15 bis 22 DSGVO hast du folgende Rechte:

| Recht | Was bedeutet das? | Wie ausüben? |
|---|---|---|
| **Auskunft** (Art. 15) | Erfragen, welche Daten wir über dich verarbeiten | E-Mail an support@watchmyai.app |
| **Berichtigung** (Art. 16) | Falsche Daten korrigieren lassen | E-Mail mit Korrekturwunsch |
| **Löschung** (Art. 17, „Recht auf Vergessen") | Account und alle Daten löschen | In der iPhone-App: Einstellungen → Account löschen |
| **Einschränkung** (Art. 18) | Verarbeitung temporär stoppen | E-Mail |
| **Datenübertragbarkeit** (Art. 20) | Daten in maschinenlesbarer Form bekommen | E-Mail — wir senden JSON-Export |
| **Widerspruch** (Art. 21) | Gegen Verarbeitung auf Basis berechtigten Interesses widersprechen | E-Mail mit Begründung |
| **Beschwerde** (Art. 77) | Bei der Aufsichtsbehörde Beschwerde einlegen | Zuständig in DE: deine Landesdatenschutzbehörde, https://www.bfdi.bund.de/DE/Service/Anschriften/Laender/Laender-node.html |

**Antwortfrist:** Wir antworten auf alle Anfragen binnen **30 Tagen** (Art. 12
Abs. 3 DSGVO), in der Regel innerhalb einer Woche.

**Kein Widerruf der Vertragserfüllung:** Da die Verarbeitung weitgehend auf
Art. 6 Abs. 1 lit. b (Vertrag) basiert, ist ein „Widerruf der Einwilligung"
nicht anwendbar — wohl aber die Account-Löschung.

---

## 12. Account-Löschung

Du kannst deinen WatchMyAI-Account jederzeit in der iPhone-App löschen:

`iPhone-App → Einstellungen → Account → Account löschen`

Bei Bestätigung werden **innerhalb von 30 Tagen** folgende Daten gelöscht:

- Account-Datensatz (Apple-User-ID, E-Mail)
- Alle Sessions
- Alle Quoten- und Nutzungsdaten
- Alle KI-Antwort-Logs
- Subscription-Verknüpfung (das aktive Abo bei Apple läuft jedoch separat
  weiter — du musst es zusätzlich in den Apple-Abo-Einstellungen kündigen)

**Nicht automatisch gelöscht:**

- Lokale Chats auf deinem iPhone / Watch / iCloud — diese musst du selbst löschen
- Anonymisierte Buchungszeilen für die steuerliche Aufbewahrung (10 Jahre, § 147 AO)

**Apple-Sign-In Widerruf:** Wenn du in deinen iPhone-Einstellungen unter
`Apple ID → Sign in with Apple → WatchMyAI → Stop using` die Verbindung
trennst, erhalten wir eine Server-zu-Server-Benachrichtigung von Apple und
löschen deinen Account ebenfalls.

---

## 13. Hinweis zu KI-generierten Inhalten (EU AI Act)

Nach Art. 50 der EU AI Act, anwendbar ab 2. August 2026, sind Anbieter
verpflichtet, Nutzer transparent darauf hinzuweisen, dass sie mit einem
KI-System interagieren.

**WatchMyAI nutzt LLM-basierte KI** (OpenAI GPT-Modelle) zur Beantwortung
deiner Fragen. Die generierten Antworten:

- Sind **automatisiert erzeugt** und nicht von einem Menschen geprüft
- Können **fehlerhaft, veraltet oder erfunden** sein („Halluzination")
- Stellen **keine** professionelle Beratung (medizinisch, juristisch,
  finanziell, sicherheitsrelevant) dar
- Solltest du **vor jeder Verwendung selbständig prüfen**

Im Onboarding und in den App-Einstellungen findest du einen sichtbaren Hinweis
zu diesen Eigenschaften der KI.

---

## 14. Sicherheit (TOM)

Wir setzen folgende technische und organisatorische Maßnahmen ein:

- **Transport-Verschlüsselung:** TLS 1.3 für sämtliche Verbindungen
- **At-Rest-Verschlüsselung:** Hetzner-Volume-Verschlüsselung für die Datenbank
- **Token-Sicherheit:** Apple-JWS-Signaturen werden geprüft, eigene Session-Tokens
  werden im iOS-Keychain (`AfterFirstUnlockThisDeviceOnly`) gespeichert
- **Rate-Limiting:** Schutz vor Brute-Force und DoS auf allen sensiblen Endpoints
- **Backups:** Regelmäßige verschlüsselte Hetzner-Snapshots
- **Geheimnisse:** API-Keys und Secrets ausschließlich in Hetzner-Secrets,
  niemals im Code-Repository
- **Datenminimierung:** Keine Übermittlung von User-Identifiern an OpenAI

---

## 15. Änderungen dieser Datenschutzerklärung

Wir aktualisieren diese Erklärung, wenn sich unsere Verarbeitung ändert.
Substanzielle Änderungen (z.B. neuer Subprozessor) werden:

- In der App beim nächsten Start mit einem Hinweis kommuniziert
- Mit mindestens **30 Tagen** Vorlaufzeit vor dem Wechsel angekündigt

**Aktuelle Version:** <<Versionsnummer, z.B. 2.0>>
**Stand:** <<Datum bei Veröffentlichung>>
**Vorgängerversionen** sind auf Anfrage erhältlich.

---

# Privacy Policy — English Translation

> The German version above is the legally binding version. The following English
> translation is provided for convenience.

(English translation TBD before EU launch — should mirror the German version.
Recommended translator: DeepL Pro with legal-domain post-editing, or a
certified legal translator for ~€300-500.)

---

**Kontakt für Datenschutzfragen:** support@watchmyai.app
**Verantwortlicher:** Siehe `IMPRESSUM.md`
**Verzeichnis Verarbeitungstätigkeiten:** Siehe internes `ROPA.md`
