# Datenschutz-Folgenabschätzung (DPIA)

> Nach **Art. 35 DSGVO** ist eine DPIA durchzuführen, wenn eine Verarbeitung
> „voraussichtlich ein hohes Risiko für die Rechte und Freiheiten natürlicher
> Personen zur Folge hat". Bei WatchMyAI ist das der Fall, weil:
>
> 1. **KI-Verarbeitung** automatisierter, möglicherweise sensibler Inhalte
>    (Art. 35 Abs. 3 lit. a — Profiling/automatisierte Entscheidung im weiten Sinne)
> 2. **Drittlandtransfer** in die USA (OpenAI) ohne DPF-Zertifizierung
> 3. **Neuartige Technologie** (LLM-basierte Antwortgenerierung)
>
> Die deutsche Aufsichtsbehörde-„Muss-Liste" für DPIA-Pflicht (BfDI) listet KI-
> Inferenzdienste explizit unter Nr. 11 — eine DPIA ist hier obligatorisch.

---

## 1. Beschreibung der Verarbeitung

### 1.1 Art und Umfang

WatchMyAI nimmt vom Nutzer eingegebene Textanfragen entgegen (max. 2.000 Zeichen),
leitet sie an die OpenAI Responses API weiter und gibt die generierte Antwort
zurück. Optional kann der Antwort-Text 30 Tage zur Kosten- und
Fehleranalyse server-seitig persistiert werden.

**Volumen:** Skaliert linear mit Nutzerzahl. Bei 1.000 zahlenden Nutzern
(Realistic-Szenario aus `WATCHMYAI_PROFITABILITAET_IREB_ANALYSE_*.md`) etwa
**400.000 KI-Anfragen pro Monat**.

### 1.2 Zweck und Umstände

| Aspekt | Beschreibung |
|---|---|
| Hauptzweck | Bereitstellung kurzer KI-Antworten auf Apple Watch |
| Sekundärzwecke | Quota-Enforcement, Kostenkontrolle, Missbrauchsprävention |
| Betroffene | Endnutzer der App (private und beruflich-private Nutzung) |
| Art der Beziehung | Vertragliche Direktbeziehung (B2C-Subscription) |
| Sensibilitätsgrad | Mittel — Prompts können personenbezogene Inhalte enthalten, sind aber nicht aktiv erhoben |

### 1.3 Datenarten

Detail siehe `ROPA.md`. Zusammenfassung für DPIA-Zweck:

| Datenart | Sensibilitätsgrad | Quelle |
|---|---|---|
| Apple-User-ID (`sub`-Claim) | Mittel (Identifier) | Apple Sign-In |
| E-Mail (optional, Apple-Relay) | Mittel | Apple Sign-In |
| Prompt-Inhalt | **Variabel: niedrig bis sehr hoch je nach User-Eingabe** | User-Tastatur |
| KI-Antwort | Wie Prompt | OpenAI |
| Quoten-Counter | Niedrig | Berechnet |
| Transaktions-IDs | Niedrig | Apple StoreKit |

### 1.4 Empfänger / Subprozessoren

- Apple Inc. (USA — DPF-zertifiziert + SCCs)
- OpenAI, L.L.C. (USA — **nicht** DPF-zertifiziert, nur SCCs)
- Hetzner Online GmbH (Deutschland — kein Drittland)

---

## 2. Notwendigkeits- und Verhältnismäßigkeitsprüfung

### 2.1 Notwendigkeit

| Frage | Antwort |
|---|---|
| Ist die Verarbeitung für den Zweck erforderlich? | Ja — ohne Übertragung des Prompts an OpenAI ist keine KI-Antwort generierbar |
| Gibt es ein milderes Mittel? | • **On-device LLM** (z.B. Apple Intelligence): aktuell qualitativ deutlich schlechter und Apple-Watch-Hardware nicht ausreichend → nicht praktikabel als alleiniger Anbieter<br>• **EU-Anbieter** (Mistral, Aleph Alpha): qualitativ noch nicht gleichwertig, Preise höher → mittelfristig prüfen, langfristig Option für „EU-Modell"-Tier<br>• **Vor-Filterung sensibler Inhalte**: technisch möglich aber UX-mindernd, würde User-Erwartung „Allzweck-AI" verletzen |
| Beschränkung der Datenmenge möglich? | • Prompt-Max-Länge 2.000 Zeichen ist hart-limitiert ✓<br>• Keine Email/User-ID im OpenAI-Request ✓<br>• Sprach-Code kann aus Locale abgeleitet werden, kein zusätzlicher Identifier nötig ✓ |

### 2.2 Verhältnismäßigkeit

**Pro Verarbeitung:**
- Nutzer hat aktiv die App installiert und Sign-In durchgeführt → impliziter Verarbeitungswunsch
- Klarer Zweck, eng auf KI-Antwort begrenzt
- Keine Werbe-/Tracking-Verwendung
- Keine Profilbildung, kein Cross-Site-Tracking
- Kostenpflichtige Abos haben transparenten Mehrwert

**Contra Verarbeitung:**
- Prompt-Inhalte können sensibel sein (Gesundheit, Beziehungen, Geheimnisse)
- Drittlandtransfer in USA mit aktuellem Schutzniveau unter EU-Standard
- OpenAI kann theoretisch trotz ZDR die Daten zur Modellverbesserung nutzen (Vertrauensrisiko, juristisch durch ZDR-Opt-In abgedeckt)
- Nutzer hat keine Kontrolle über die Modellauswahl und das exakte Routing

**Abwägung:** Die Verarbeitung ist verhältnismäßig, weil
1. der Zweck legitim und vertraglich vereinbart ist,
2. die Datenminimierung weitgehend umgesetzt ist (kein zusätzlicher Identifier an OpenAI),
3. die Aufbewahrungsfrist server-seitig auf 30 Tage begrenzt ist,
4. der Nutzer transparent informiert und der Verarbeitung beim Sign-In aktiv zustimmt,
5. das Recht auf Löschung jederzeit ausgeübt werden kann.

---

## 3. Risiko-Analyse

Methodik: Eintrittswahrscheinlichkeit × Schadensschwere (jeweils 1-4).

| ID | Risiko | Eintritts-W. | Schadensschwere | Risiko-Score |
|---|---|---|---|---|
| R1 | User gibt versehentlich sensible Daten (Gesundheit, Passwort, Geheimnisse) in Prompt ein | **4** (regelmäßig) | **3** (bei sensiblen Daten auch jenseits Art. 9) | **12** |
| R2 | OpenAI nutzt Prompts trotz ZDR-Vereinbarung zur Modellverbesserung | 2 (Vertragsbruch oder Konfigurationsfehler) | 4 (irreversible Datenpreisgabe) | 8 |
| R3 | OpenAI wird in USA von Behörden zur Datenherausgabe gezwungen (CLOUD Act) | 2 (rechtl. Möglichkeit besteht) | 3 (Schadensumfang vom Inhalt abhängig) | 6 |
| R4 | Backend-Datenbank-Leak inkl. `ai_request_log.answer` | 2 (bei guten TOMs) | 3 (Antworten enthalten ggf. Hinweise auf Prompts) | 6 |
| R5 | Versehentliche Übermittlung der Apple-User-ID an OpenAI durch Code-Bug | 1 (aktuell verifiziert nicht der Fall) | 3 (Re-Identifizierung möglich) | 3 |
| R6 | Account-Übernahme durch gestohlenes Apple-Token | 1 (Apple-Sicherheit hoch) | 3 (Zugriff auf Chat-Historie) | 3 |
| R7 | Subprozessor wechselt Verarbeitungsbedingungen einseitig ohne Information | 2 (passiert in der Praxis) | 2 (kann gemeldet werden) | 4 |
| R8 | User wird durch fehlerhafte KI-Antwort geschädigt (medizinisch, finanziell, rechtlich) | 3 (hohe Eintritts-W. bei Allzweck-AI) | 4 (kann erheblich sein) | 12 |

**Hauptrisiken (Score ≥ 8):** R1, R2, R8

---

## 4. Geplante Abhilfemaßnahmen

### Gegen R1 — User gibt sensible Daten ein

**Technisch:**
- ✅ Hard-Limit 2.000 Zeichen pro Prompt (begrenzt Massendaten-Eintrag)
- ✅ Watch-UI limitiert durch Hardware natürlich kurze Eingaben
- ⏳ **TBD:** Pre-Submit-Hint in iOS-App „Gib keine Passwörter, Gesundheitsdaten oder Kontodaten ein" beim ersten Start
- ⏳ **TBD:** In den Settings „Sicherheitshinweise" mit Use-Case-Negativliste

**Organisatorisch:**
- ✅ Privacy Policy beschreibt Datenfluss in einfacher Sprache
- ⏳ **TBD:** AI-Act-konforme „Du sprichst mit einer KI"-Hinweis im Onboarding (siehe `DSGVO-7`)

### Gegen R2 — OpenAI nutzt Daten trotz ZDR

**Vertraglich:**
- ✅ DPA mit SCCs Modul 2 (Controller-Processor) ausnahmslos signiert
- ⏳ **TBD:** ZDR-Opt-In schriftlich von OpenAI bestätigen lassen, alle 6 Monate Reverifizierung
- ⏳ **TBD:** TIA (Transfer Impact Assessment) als Backup-Dokumentation

**Technisch:**
- ⏳ **TBD:** Logging im Backend dass jeder OpenAI-Request mit `store: false` und ohne `user`-Identifier-Feld gesendet wird
- ⏳ **TBD:** Halbjährliches Audit: Stichprobe von 10 OpenAI-Requests in `ai_request_log` analysieren, ob versehentlich personenbezogene Felder mitgesendet wurden

### Gegen R8 — Schaden durch fehlerhafte KI-Antwort

**Organisatorisch / Vertraglich:**
- ✅ Terms of Use schließen „high-risk decisions" aus (siehe `LegalPageController` → `/terms`)
- ⏳ **TBD:** In der App vor jeder Antwort (bei kritischen Intents wie „medical", „legal", „financial") eine sichtbare Warnung „Keine professionelle Beratung"
- ⏳ **TBD:** Keyword-Filter bei besonders heiklen Themen (Suizid, Selbstverletzung, Notfälle) → Antwort wird durch hardgecodete Notruf-Hinweise ersetzt

**Marketing:**
- ⏳ **TBD:** In Marketing-Materialien KEINE Behauptungen wie „Beratung", „Hilfe" o.ä. verwenden — nur „kurze Antworten", „Recherchehilfe"

### Gegen R3, R4, R5, R6, R7 — siehe ROPA TOM-Spalten

---

## 5. Restrisiko-Bewertung

Nach Umsetzung der oben markierten ⏳-Maßnahmen sinken die Risiko-Scores auf:

| ID | Restrisiko | Bewertung |
|---|---|---|
| R1 | 6 (mittlere E.-W. × mittlere Schwere durch Hinweise) | Akzeptabel mit Disclosure |
| R2 | 4 (durch ZDR-Verifikation reduziert) | Akzeptabel |
| R8 | 6 (durch Disclaimer reduziert, aber nicht eliminierbar) | Akzeptabel mit AGB-Schutz |
| Übrige | ≤ 4 | Akzeptabel |

**Gesamteinschätzung:** Nach Implementierung der ⏳-Maßnahmen ist das Restrisiko
verhältnismäßig. **Keine vorherige Konsultation der Aufsichtsbehörde
(Art. 36 DSGVO) erforderlich.**

---

## 6. Konsultation Betroffener

Eine Befragung von Betroffenen nach Art. 35 Abs. 9 DSGVO ist bei
Endkonsumenten-Apps üblicherweise nicht praktikabel. Substitut:

- **TestFlight-Beta-Feedback** während der Validierungsphase aktiv einholen,
  insbesondere zu Datenschutz-Bedenken
- **App-Store-Bewertungen** in den ersten 90 Tagen nach Live-Gang systematisch
  auf Datenschutz-relevante Beschwerden durchsuchen
- **Support-Email-Anfragen** kategorisieren und Datenschutz-Vorfälle separat
  dokumentieren

---

## 7. Konsultation Datenschutzbeauftragter

Nicht erforderlich, da kein DSB bestellt (siehe `ROPA.md` § 0). Ersatzweise:

- Diese DPIA wurde durch den Verantwortlichen selbst erstellt
- Vor Live-Gang in der EU empfohlen: einmalige fachjuristische Außenprüfung
  durch DE-Datenschutzanwalt (Aufwand ~500-1.500 €)

---

## 8. Überprüfung und Aktualisierung

| Trigger | Aktion |
|---|---|
| Jährliche Routine | DPIA jeden 1. Januar reviewen |
| Neuer Subprozessor | DPIA-Risikoanalyse erweitern |
| Neue Verarbeitungstätigkeit (z.B. Voice-Eingabe) | Komplette DPIA-Wiederholung |
| Sicherheitsvorfall | Anlassbezogene DPIA-Aktualisierung |
| Änderung OpenAI-DPA | Kapitel 4 / R2 aktualisieren |
| Verbraucherzentrale-Beschwerde | DPIA + ROPA + AVV-Checklist als Antwort vorlegen |

---

## 9. Dokumentation der Entscheidung

> Diese DPIA wurde durch <<Vollständiger Name>> als Verantwortlicher i.S.d. Art. 4
> Nr. 7 DSGVO erstellt und nach bestem Wissen geprüft.
>
> Die Verarbeitung ist nach Abwägung der Notwendigkeit, der getroffenen
> Schutzmaßnahmen und des Restrisikos zulässig und mit Art. 35 DSGVO konform.
>
> **Datum der Erstellung:** <<Datum>>
> **Datum nächste Überprüfung:** <<Datum + 12 Monate>>
> **Unterschrift / Bestätigung:** <<Name>>

---

**Hinweis:** Dieses Dokument ist ein Requirements- und Risikodokument, keine
juristische Beratung. Vor einem EU-Launch mit zahlenden Nutzern wird eine
fachjuristische Freigabe empfohlen.
