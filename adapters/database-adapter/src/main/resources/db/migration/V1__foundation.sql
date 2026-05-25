CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    max_per_payment NUMERIC(19, 2),
    daytime_daily_limit NUMERIC(19, 2),
    nighttime_daily_limit NUMERIC(19, 2),
    weekend_daily_limit NUMERIC(19, 2),
    daily_transaction_limit INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE wallet_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    policy_id UUID NOT NULL REFERENCES policies(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_policies_name ON policies(name);
CREATE INDEX idx_policies_category ON policies(category);
CREATE INDEX idx_wallet_policies_wallet_id ON wallet_policies(wallet_id);
CREATE INDEX idx_wallet_policies_policy_id ON wallet_policies(policy_id);
