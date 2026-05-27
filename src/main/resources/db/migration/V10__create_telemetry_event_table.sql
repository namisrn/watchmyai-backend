-- Product telemetry — datasparsam, ohne Prompt-/Antwort-Inhalte.
-- Privacy-Pflichten: siehe legal/ROPA.md § 8 (wird mit dieser Migration in den
-- ROPA-Eintrag § 8 "Produkt-Telemetrie" überführt) und legal/PRIVACY_POLICY.md.
--
-- Inhalt: nur Funnel-/Conversion-Events mit Metadaten (Plan, Plattform, Locale).
-- KEINE Prompts, KEINE Antworten, KEINE Email/User-ID. user_id_hash ist SHA-256
-- der internen userId (truncated 32 hex chars = 128 bit) — reicht für Kohorten-
-- Analyse, ist aber nicht direkt re-identifizierbar ohne separaten Mapping-Table
-- (den wir nicht halten).

CREATE TABLE telemetry_event (
    id BIGSERIAL PRIMARY KEY,
    event_name VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- VARCHAR statt CHAR damit Hibernates Schema-Validator zufrieden ist
    -- (Entity-Mapping ist String -> @Column length=32, mappt zu VARCHAR).
    user_id_hash VARCHAR(32),
    platform VARCHAR(16),
    plan VARCHAR(16),
    -- Locale-Code wie "de-DE", "en-US"; nullable für Server-side Events
    locale VARCHAR(16),
    -- App-Build-Version (z.B. "1.2.0+48") für Version-Cohort-Analyse
    app_version VARCHAR(32),
    -- Zusätzliche Event-spezifische Properties als JSON-String. Bewusst kein
    -- JSONB-Typ um keine zusätzliche Hibernate-Annotation oder Library zu
    -- brauchen; für die typischen Dashboard-Queries reicht event_name+columns
    -- oben. Falls später viel JSON-Querying gebraucht wird: ALTER COLUMN auf
    -- jsonb in einer späteren Migration.
    properties_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_telemetry_event_name_lowercase
        CHECK (event_name = LOWER(event_name)),
    CONSTRAINT chk_telemetry_event_platform
        CHECK (platform IS NULL OR platform IN ('ios', 'watch', 'backend')),
    CONSTRAINT chk_telemetry_event_plan
        CHECK (plan IS NULL OR plan IN ('free', 'plus', 'pro'))
);

-- Funnel-Queries ("wie viele aktivieren heute?"). Composite-Index erschlägt
-- die zwei häufigsten Filter zusammen.
CREATE INDEX idx_telemetry_event_name_occurred
    ON telemetry_event(event_name, occurred_at DESC);

-- Kohorten-Queries pro User ("hat User X jemals upgradet?"). Kein Partial-Index
-- mit WHERE-Clause, weil H2 (Testdatenbank) das nicht unterstützt — die paar
-- NULL-Zeilen in dem Index sind in der Produktion akzeptabel.
CREATE INDEX idx_telemetry_event_user_hash_occurred
    ON telemetry_event(user_id_hash, occurred_at DESC);

COMMENT ON TABLE telemetry_event IS
    'Product funnel and conversion telemetry. PII-free by design. See legal/ROPA.md § 8.';
COMMENT ON COLUMN telemetry_event.user_id_hash IS
    'SHA-256(internal_user_id) truncated to 128 bit hex. Allows cohort analysis without re-identification.';
COMMENT ON COLUMN telemetry_event.properties_json IS
    'Sanitized JSON. PII property keys (prompt, content, answer, email, ...) are rejected at the service layer.';
