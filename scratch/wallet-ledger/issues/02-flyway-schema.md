# 02: Flyway schema

**What to build:** the versioned SQL migration that creates the three tables — `player`, `wallet` (with `player_id`, `balance`, `version`), and `ledger_entry` (with `wallet_id`, `amount`, `direction`, `reason_kind`, `description`, `reference_id`, `request_id`, `original_debit_id`, `created_at`) — including the UNIQUE constraint on the Idempotency Key (`request_id`) and the UNIQUE-but-nullable constraint on `original_debit_id`. Flyway must apply it on startup and record it in the schema history.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] Application boots and Flyway applies the migration without error
- [ ] Schema history records the migration (version, checksum, success)
- [ ] All three tables exist with their columns and the UNIQUE constraints