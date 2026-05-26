CREATE TABLE payment_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_audit_wallet_id ON payment_audit_events(wallet_id);
CREATE INDEX idx_payment_audit_created_at ON payment_audit_events(created_at);
