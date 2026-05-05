CREATE TABLE app_store_subscription (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    original_transaction_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    plan_type VARCHAR(50) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(100),
    grace_period BOOLEAN NOT NULL DEFAULT FALSE,
    billing_retry BOOLEAN NOT NULL DEFAULT FALSE,
    verification_source VARCHAR(100) NOT NULL,
    last_notification_type VARCHAR(100),
    last_notification_subtype VARCHAR(100),
    last_verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_store_subscription_original_transaction UNIQUE (original_transaction_id)
);

CREATE INDEX idx_app_store_subscription_user_active
    ON app_store_subscription(user_id, active);
