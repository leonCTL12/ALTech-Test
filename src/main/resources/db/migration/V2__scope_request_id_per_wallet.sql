-- Scope the idempotency key to its wallet: the same key may be used by
-- different wallets without one player's operation being swallowed by another's.
-- A key still identifies a single logical request per wallet; the constraint
-- remains the final guard against double-application within that wallet.
ALTER TABLE ledger_entry DROP CONSTRAINT uq_ledger_request_id;
ALTER TABLE ledger_entry ADD CONSTRAINT uq_ledger_request_id UNIQUE (wallet_id, request_id);