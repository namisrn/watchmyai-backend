package com.watchmyai.legal;

import com.watchmyai.config.LegalProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegalPageController {

    private final LegalProperties legalProperties;

    public LegalPageController(LegalProperties legalProperties) {
        this.legalProperties = legalProperties;
    }

    // Maßgeblich (rechtlich bindend) ist die deutsche Fassung in legal/PRIVACY_POLICY.md.
    // Diese ausgelieferte Seite ist die in der App + im App Store verlinkte Datenschutz-
    // erklärung und MUSS inhaltlich mit legal/PRIVACY_POLICY.md synchron gehalten werden
    // (DSGVO Art. 13 + EU AI Act Art. 50). Betreiberdaten kommen aus watchmyai.legal.*.
    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> privacy() {
        String contactEmail = escape(legalProperties.contactEmail());
        String controller = line(legalProperties.operatorName())
                + line(legalProperties.operatorAddress())
                + line(legalProperties.operatorPostalCity())
                + line(legalProperties.operatorCountry())
                + mailto(legalProperties.contactEmail());

        String body = """
                <h1>Datenschutzerklärung — WatchMyAI</h1>
                <p>Diese Erklärung informiert dich nach Art. 13 DSGVO darüber, welche personenbezogenen
                Daten WatchMyAI verarbeitet, zu welchem Zweck und auf welcher Rechtsgrundlage.</p>

                <h2>1. Verantwortlicher</h2>
                <p>Verantwortlicher i.S.d. Art. 4 Nr. 7 DSGVO ist:</p>
                __CONTROLLER__
                <p>Eine Pflicht zur Benennung eines Datenschutzbeauftragten besteht nicht
                (Solo-Unternehmer, keine umfangreiche Beobachtung, keine besonderen Datenkategorien
                nach Art. 37 Abs. 1 lit. b/c DSGVO).</p>

                <h2>2. Zwecke und Rechtsgrundlagen</h2>
                <table>
                  <tr><th>Zweck</th><th>Daten</th><th>Rechtsgrundlage</th></tr>
                  <tr><td>Account erstellen und einloggen</td><td>Apple-User-ID (Sign in with Apple)</td><td>Vertrag (Art. 6 Abs. 1 lit. b)</td></tr>
                  <tr><td>KI-Antworten generieren</td><td>Deine eingegebene Frage</td><td>Vertrag (Art. 6 Abs. 1 lit. b)</td></tr>
                  <tr><td>Plus/Pro-Abo verwalten</td><td>Apple-Transaktions-ID</td><td>Vertrag (Art. 6 Abs. 1 lit. b)</td></tr>
                  <tr><td>Tages-/Monatslimits durchsetzen</td><td>Anzahl deiner Anfragen</td><td>Vertrag + berechtigtes Interesse (lit. b/f)</td></tr>
                  <tr><td>Kostenanalyse und Reklamation</td><td>Gespeicherte Antworten (30 Tage)</td><td>berechtigtes Interesse (lit. f)</td></tr>
                  <tr><td>Steuerliche Aufbewahrung</td><td>Anonymisierte Buchungszeilen</td><td>Rechtspflicht (lit. c, § 147 AO)</td></tr>
                </table>
                <p><strong>Wir sammeln NICHT:</strong> Standort, Kontakte, Browser-Verlauf, Werbe-IDs,
                Tracking-Daten, biometrische Daten oder Profildaten zu Marketingzwecken. Wir verkaufen
                keine personenbezogenen Daten und nutzen sie nicht für Drittanbieter-Werbetracking.</p>

                <h2>3. Sign in with Apple</h2>
                <p>Zur Nutzung ist eine Anmeldung über Sign in with Apple erforderlich. Apple übermittelt
                uns deine Apple-User-ID und eine E-Mail-Adresse (echt oder Apple-Private-Relay, je nach
                deiner Wahl); ein Name wird nicht angefordert. Die Apple-User-ID dient ausschließlich der
                Wiedererkennung deines Accounts. Rechtsgrundlage: Art. 6 Abs. 1 lit. b. Speicherdauer:
                bis zur Account-Löschung.</p>

                <h2>4. KI-Anfragen — wer sieht deine Fragen?</h2>
                <p>Datenfluss: <em>Deine Eingabe → WatchMyAI-Server (Hetzner, Deutschland) → OpenAI
                Responses API (USA) → Antwort zurück.</em></p>
                <ul>
                  <li><strong>Backend (Hetzner DE):</strong> Der Fragetext wird nur durchgeleitet und
                  <strong>nicht gespeichert</strong> (0 Sekunden). Der Antworttext wird für Kostenanalyse
                  und Reklamationsbearbeitung <strong>30 Tage</strong> gespeichert und danach automatisch gelöscht.</li>
                  <li><strong>OpenAI (USA):</strong> verarbeitet die Frage und generiert die Antwort.
                  Der Request setzt <code>store=false</code> und enthält <strong>keine</strong> Apple-User-ID,
                  keine E-Mail und keine Geräte-IDs (Datenminimierung).</li>
                </ul>
                <div class="note"><strong>Drittlandtransfer USA (Art. 44 DSGVO):</strong> OpenAI verarbeitet
                deine Frage in den USA, einem unsicheren Drittland. Schutzmaßnahmen: Standard Contractual
                Clauses (EU-Kommission 2021/914, Modul 2) im Vertrag mit OpenAI sowie eine schriftlich
                bestätigte „Zero Data Retention"-Konfiguration. Wer keine US-Verarbeitung wünscht, kann die
                App nicht nutzen; eine EU-Modellroute ist derzeit nicht verfügbar.</div>

                <h2>5. Sensible Inhalte</h2>
                <div class="note">Gib in der App <strong>keine sensiblen Daten</strong> ein (Passwörter,
                PINs, API-Keys, Gesundheits- oder Bankdaten, personenbezogene Daten Dritter,
                Geschäftsgeheimnisse). WatchMyAI ist kein zertifiziertes Werkzeug für besondere
                Datenkategorien nach Art. 9 DSGVO; bei Verwendung solcher Inhalte trägst du das Risiko selbst.</div>

                <h2>6. Subscription-Verwaltung</h2>
                <p>Bei Plus/Pro verarbeiten wir über die App Store Server API: Produkt-ID, Transaktions- und
                Original-Transaktions-ID, Ablauf-/Renewal-Status sowie eine interne UUID (<code>appAccountToken</code>).
                <strong>Zahlungsdaten (Kreditkarte, IBAN o.ä.) erhalten wir von Apple nicht.</strong>
                Rechtsgrundlage: Art. 6 Abs. 1 lit. b.</p>

                <h2>7. Empfänger und Subprozessoren (Art. 28 DSGVO)</h2>
                <table>
                  <tr><th>Anbieter</th><th>Zweck</th><th>Sitz</th><th>Rechtsrahmen</th></tr>
                  <tr><td>Apple Inc.</td><td>Sign in with Apple, App Store, IAP, APNs, iCloud-Sync</td><td>USA / EU</td><td>DPA + SCCs + DPF</td></tr>
                  <tr><td>OpenAI, L.L.C.</td><td>KI-Antwort-Generierung</td><td>USA</td><td>DPA + SCCs (Modul 2)</td></tr>
                  <tr><td>Hetzner Online GmbH</td><td>Server, Datenbank, Backups</td><td>Deutschland</td><td>AVV, kein Drittland</td></tr>
                </table>

                <h2>8. Speicherfristen</h2>
                <table>
                  <tr><th>Datenart</th><th>Dauer</th></tr>
                  <tr><td>Apple-User-ID, E-Mail</td><td>bis Account-Löschung</td></tr>
                  <tr><td>Session-Token</td><td>30 Tage rolling</td></tr>
                  <tr><td>Fragetext im Backend</td><td>0 Sekunden (nicht gespeichert)</td></tr>
                  <tr><td>KI-Antwort</td><td>30 Tage, danach gelöscht</td></tr>
                  <tr><td>Quoten-Counter / Kostenmetadaten</td><td>aktuelle Periode + 24 Monate</td></tr>
                  <tr><td>Transaktions-IDs (steuerlich)</td><td>bis Löschung + 10 Jahre anonymisiert (§ 147 AO)</td></tr>
                  <tr><td>Lokale Chat-Historie</td><td>bis du sie löschst / die App deinstallierst</td></tr>
                </table>

                <h2>9. Deine Rechte</h2>
                <p>Nach Art. 15–22 DSGVO hast du das Recht auf Auskunft, Berichtigung, Löschung,
                Einschränkung, Datenübertragbarkeit und Widerspruch. Anfragen an die unten genannte
                Kontaktadresse; wir antworten binnen 30 Tagen (Art. 12 Abs. 3).</p>
                <p><strong>Beschwerderecht (Art. 77):</strong> Du kannst dich bei einer Aufsichtsbehörde
                beschweren. Zuständige Behörden in Deutschland:
                <a href="https://www.bfdi.bund.de/DE/Service/Anschriften/Laender/Laender-node.html">Übersicht der Landesdatenschutzbehörden</a>.</p>

                <h2>10. Account-Löschung</h2>
                <p>Du kannst deinen Account jederzeit in der iPhone-App löschen
                (<em>Einstellungen → Account → Account löschen</em>). Innerhalb von 30 Tagen werden
                Account-Datensatz, Sessions, Nutzungs-/Quotendaten, KI-Antwort-Logs und die
                Subscription-Verknüpfung gelöscht. Ein aktives Apple-Abo läuft separat weiter und ist
                zusätzlich in den Apple-Abo-Einstellungen zu kündigen. Lokale Chats auf deinen Geräten/iCloud
                musst du selbst löschen.</p>

                <h2>11. Hinweis zu KI-generierten Inhalten (EU AI Act, Art. 50)</h2>
                <p>WatchMyAI nutzt LLM-basierte KI (OpenAI GPT-Modelle) zur Beantwortung deiner Fragen.
                Die Antworten sind <strong>automatisiert erzeugt</strong> und nicht von einem Menschen geprüft,
                können <strong>fehlerhaft, veraltet oder erfunden</strong> sein und stellen <strong>keine
                professionelle Beratung</strong> (medizinisch, juristisch, finanziell, sicherheitsrelevant) dar.
                Prüfe Antworten vor jeder Verwendung selbständig. Ein sichtbarer Hinweis findet sich auch im
                Onboarding und in den App-Einstellungen.</p>

                <h2>12. Sicherheit</h2>
                <p>Technische und organisatorische Maßnahmen u.a.: TLS-Transportverschlüsselung,
                At-Rest-Verschlüsselung der Datenbank, geprüfte Apple-JWS-Signaturen, Keychain-gespeicherte
                Session-Tokens, Rate-Limiting, verschlüsselte Backups und Datenminimierung gegenüber OpenAI.</p>

                <h2>13. Änderungen</h2>
                <p>Substanzielle Änderungen (z.B. ein neuer Subprozessor) werden in der App beim nächsten Start
                mit mindestens 30 Tagen Vorlauf angekündigt.</p>

                <h2>Kontakt</h2>
                <p>Datenschutzanfragen: <a href="mailto:__CONTACT_EMAIL__">__CONTACT_EMAIL__</a></p>
                """
                .replace("__CONTROLLER__", controller)
                .replace("__CONTACT_EMAIL__", contactEmail);

        return ResponseEntity.ok(page("WatchMyAI Datenschutzerklärung", body, "de"));
    }

    @GetMapping(value = "/impressum", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> impressum() {
        // Anbieterkennzeichnung nach § 5 DDG. Die konkreten Betreiberdaten kommen
        // aus der Konfiguration (watchmyai.legal.*) und werden über Umgebungs-
        // variablen gesetzt. Nicht gesetzte Felder werden ausgelassen statt mit
        // Platzhaltern gerendert — eine halb ausgefüllte Anbieterkennzeichnung wäre
        // schlechter als eine schlanke. Inhaltlich identisch zu legal/IMPRESSUM.md.
        String body = "<h1>Impressum</h1>"
                + "<p>Angaben gemäß § 5 DDG (Digitale-Dienste-Gesetz):</p>"
                + line(legalProperties.operatorName())
                + line(legalProperties.operatorAddress())
                + line(legalProperties.operatorPostalCity())
                + line(legalProperties.operatorCountry())
                + "<h2>Kontakt</h2>"
                + mailto(legalProperties.contactEmail())
                + labelledLine("USt-IdNr.:", legalProperties.vatId());
        return ResponseEntity.ok(page("WatchMyAI Impressum", body, "de"));
    }

    @GetMapping(value = "/terms", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> terms() {
        String contactEmail = escape(legalProperties.contactEmail());
        return ResponseEntity.ok(page(
                "WatchMyAI Terms of Use",
                """
                        <h1>WatchMyAI Terms of Use</h1>
                        <p>WatchMyAI provides short AI answers for iPhone and Apple Watch. You are responsible for reviewing AI output before relying on it.</p>
                        <h2>Subscriptions</h2>
                        <p>Plus and Pro subscriptions renew automatically until cancelled in your Apple Account. Manage or cancel subscriptions in the App Store subscription settings.</p>
                        <p>Usage limits, plan features, and model availability may change to keep the service reliable and economically sustainable.</p>
                        <p>Do not use WatchMyAI for illegal, harmful, abusive, or high-risk decisions where professional advice is required.</p>
                        <p>Contact: <a href="mailto:__CONTACT_EMAIL__">__CONTACT_EMAIL__</a></p>
                        """.replace("__CONTACT_EMAIL__", contactEmail)
        ));
    }

    private String page(String title, String body) {
        return page(title, body, "en");
    }

    private String page(String title, String body, String lang) {
        return """
                <!doctype html>
                <html lang="__LANG__">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>__TITLE__</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.55; max-width: 760px; margin: 40px auto; padding: 0 20px; color: #111827; }
                    h1 { line-height: 1.15; }
                    h2 { margin-top: 1.8em; }
                    a { color: #0f766e; }
                    table { border-collapse: collapse; width: 100%; margin: 0.5em 0; }
                    th, td { border: 1px solid #d1d5db; padding: 6px 8px; text-align: left; vertical-align: top; font-size: 0.95em; }
                    th { background: #f3f4f6; }
                    .note { background: #fef3c7; border-left: 4px solid #f59e0b; padding: 10px 14px; border-radius: 6px; }
                  </style>
                </head>
                <body>__BODY__</body>
                </html>
                """
                .replace("__LANG__", escape(lang))
                .replace("__TITLE__", escape(title))
                .replace("__BODY__", body);
    }

    /** Rendert eine Zeile nur, wenn der Wert gesetzt ist; sonst leer. */
    private String line(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "<p>" + escape(value) + "</p>";
    }

    /** Wie {@link #line(String)}, aber mit vorangestelltem Label (z. B. "USt-IdNr.:"). */
    private String labelledLine(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "<p>" + escape(label) + " " + escape(value) + "</p>";
    }

    private String mailto(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        String escaped = escape(email);
        return "<p><a href=\"mailto:" + escaped + "\">" + escaped + "</a></p>";
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
