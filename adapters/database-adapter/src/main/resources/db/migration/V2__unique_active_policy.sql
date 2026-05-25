CREATE UNIQUE INDEX IF NOT EXISTS idx_wallet_policies_unique_active
ON wallet_policies (wallet_id)
WHERE active = TRUE;
