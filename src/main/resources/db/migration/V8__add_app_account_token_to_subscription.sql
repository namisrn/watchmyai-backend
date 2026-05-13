ALTER TABLE app_store_subscription
    ADD COLUMN app_account_token UUID;

CREATE INDEX idx_app_store_subscription_app_account_token
    ON app_store_subscription(app_account_token);
