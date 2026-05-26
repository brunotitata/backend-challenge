CREATE UNIQUE INDEX idx_policies_name_unique ON policies(name);

INSERT INTO policies (
    name,
    category,
    max_per_payment,
    daytime_daily_limit,
    nighttime_daily_limit,
    weekend_daily_limit
)
SELECT
    'DEFAULT_VALUE_LIMIT',
    'VALUE_LIMIT',
    1000.00,
    4000.00,
    1000.00,
    1000.00
WHERE NOT EXISTS (
    SELECT 1 FROM policies WHERE name = 'DEFAULT_VALUE_LIMIT'
);

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_owner_name_not_blank
    CHECK (TRIM(owner_name) <> '');

ALTER TABLE policies
    ADD CONSTRAINT chk_policies_category
    CHECK (category IN ('VALUE_LIMIT', 'TX_COUNT_LIMIT'));

ALTER TABLE policies
    ADD CONSTRAINT chk_policies_value_limit_fields
    CHECK (
        category <> 'VALUE_LIMIT'
        OR (
            max_per_payment IS NOT NULL
            AND max_per_payment > 0
            AND daytime_daily_limit IS NOT NULL
            AND daytime_daily_limit > 0
            AND nighttime_daily_limit IS NOT NULL
            AND nighttime_daily_limit > 0
            AND weekend_daily_limit IS NOT NULL
            AND weekend_daily_limit > 0
            AND daily_transaction_limit IS NULL
        )
    );

ALTER TABLE policies
    ADD CONSTRAINT chk_policies_tx_count_fields
    CHECK (
        category <> 'TX_COUNT_LIMIT'
        OR (
            daily_transaction_limit IS NOT NULL
            AND daily_transaction_limit > 0
            AND max_per_payment IS NULL
            AND daytime_daily_limit IS NULL
            AND nighttime_daily_limit IS NULL
            AND weekend_daily_limit IS NULL
        )
    );

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_amount_positive
    CHECK (amount > 0);

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_period_type
    CHECK (period_type IN ('DAYTIME', 'NIGHTTIME', 'WEEKEND'));

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('APPROVED', 'REJECTED'));

ALTER TABLE limit_consumptions
    ADD CONSTRAINT chk_limit_consumptions_non_negative
    CHECK (consumed_amount >= 0 AND transaction_count >= 0);

ALTER TABLE payment_idempotency_keys
    ADD CONSTRAINT chk_payment_idempotency_response_status
    CHECK (response_status IN (0, 201, 422));

ALTER TABLE payment_audit_events
    ADD COLUMN payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    ADD COLUMN policy_id UUID REFERENCES policies(id),
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN request_id VARCHAR(255);

ALTER TABLE payment_audit_events
    ADD CONSTRAINT chk_payment_audit_status
    CHECK (status IN ('APPROVED', 'REJECTED'));

CREATE INDEX idx_payments_approved_wallet_occurred_id
    ON payments(wallet_id, occurred_at, id)
    WHERE status = 'APPROVED';

CREATE INDEX idx_payment_audit_payment_id ON payment_audit_events(payment_id);
CREATE INDEX idx_payment_audit_policy_id ON payment_audit_events(policy_id);
CREATE INDEX idx_payment_audit_request_id ON payment_audit_events(request_id);
