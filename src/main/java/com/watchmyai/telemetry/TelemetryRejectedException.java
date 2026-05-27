package com.watchmyai.telemetry;

/**
 * Wirft die TelemetryService, wenn ein Event gegen die Sanitization-Regeln verstößt
 * (verbotene Property-Keys, falscher Event-Name, ungültige Enum-Werte).
 *
 * <p>Bewusst eine RuntimeException — Telemetrie soll nie eine Business-Action
 * abbrechen. Aufrufer fangen das in einem try/catch + WARN-Log, die Geschäftslogik
 * läuft weiter.
 */
public class TelemetryRejectedException extends RuntimeException {

    public TelemetryRejectedException(String message) {
        super(message);
    }

    public TelemetryRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
