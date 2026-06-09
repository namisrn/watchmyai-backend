CREATE TABLE device_attestation (
    id                 BIGSERIAL PRIMARY KEY,
    key_id             VARCHAR(512) NOT NULL,
    user_id            VARCHAR(255) NOT NULL,
    attestation_object BYTEA NOT NULL,
    sign_count         BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_device_attestation_key_id UNIQUE (key_id),
    CONSTRAINT chk_device_attestation_sign_count_non_negative CHECK (sign_count >= 0)
);

CREATE INDEX idx_device_attestation_user_id ON device_attestation(user_id);
