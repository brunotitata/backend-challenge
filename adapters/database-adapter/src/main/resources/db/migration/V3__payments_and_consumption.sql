CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    policy_id UUID NOT NULL REFERENCES policies(id),
    amount NUMERIC(19, 2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE limit_consumptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    policy_id UUID NOT NULL REFERENCES policies(id),
    period_type VARCHAR(20) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    consumed_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_wallet_occurred_id ON payments(wallet_id, occurred_at, id);
CREATE INDEX idx_payments_wallet_policy_period ON payments(wallet_id, policy_id, period_type, period_start);
CREATE UNIQUE INDEX idx_limit_consumptions_unique ON limit_consumptions(wallet_id, policy_id, period_type, period_start);
