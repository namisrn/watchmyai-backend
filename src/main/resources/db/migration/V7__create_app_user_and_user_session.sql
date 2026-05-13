CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    apple_subject VARCHAR(255) NOT NULL,
    apple_user_id VARCHAR(255),
    email VARCHAR(320),
    app_account_token UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_user_user_id UNIQUE (user_id),
    CONSTRAINT uk_app_user_apple_subject UNIQUE (apple_subject),
    CONSTRAINT uk_app_user_app_account_token UNIQUE (app_account_token)
);

CREATE TABLE user_session (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    source VARCHAR(20) NOT NULL,
    device_name VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_session_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_user_session_user_active
    ON user_session(user_id, revoked_at, expires_at);
