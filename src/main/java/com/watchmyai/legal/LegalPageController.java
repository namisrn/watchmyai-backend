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

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> privacy() {
        String contactEmail = escape(legalProperties.contactEmail());
        return ResponseEntity.ok(page(
                "WatchMyAI Privacy Policy",
                """
                        <h1>WatchMyAI Privacy Policy</h1>
                        <p>WatchMyAI uses Sign in with Apple to create your account session and to unlock the Apple Watch app.</p>
                        <p>AI prompts and generated answers are sent to the WatchMyAI backend and onward to OpenAI only to provide the requested AI response, enforce usage limits, prevent abuse, and operate the service.</p>
                        <p>Subscription status and App Store transaction identifiers are processed to provide Plus and Pro entitlements. Local chat history is stored on your device with SwiftData and may sync privately through your iCloud account when enabled by the system.</p>
                        <p>We do not sell personal data and do not use your data for third-party advertising tracking.</p>
                        <p>You can delete your WatchMyAI account from Settings in the iPhone app. Deletion removes account sessions, service usage history, subscription associations, and synced chat history. Any Apple subscription must be cancelled separately in your Apple Account.</p>
                        <p>Contact: <a href="mailto:__CONTACT_EMAIL__">__CONTACT_EMAIL__</a></p>
                        """.replace("__CONTACT_EMAIL__", contactEmail)
        ));
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
        return ResponseEntity.ok(page("WatchMyAI Impressum", body));
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
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>__TITLE__</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.55; max-width: 760px; margin: 40px auto; padding: 0 20px; color: #111827; }
                    h1 { line-height: 1.15; }
                    a { color: #0f766e; }
                  </style>
                </head>
                <body>__BODY__</body>
                </html>
                """
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
