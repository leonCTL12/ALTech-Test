# 03: JPA entities and repositories

**What to build:** the JPA entities `Player`, `Wallet`, and `LedgerEntry` mapped to the Flyway schema, plus Spring Data `JpaRepository`s for each. A Wallet belongs to one Player; a Ledger Entry belongs to one Wallet. Inserting a second Ledger Entry with the same Idempotency Key must fail on the database UNIQUE constraint.

**Blocked by:** 02 Flyway schema

**Status:** ready-for-agent

- [x] A `@DataJpaTest` saves and reads back a Player
- [x] A Wallet saves and reads back with its Player relationship
- [x] A Ledger Entry saves and reads back with its Wallet relationship, including the Reason fields
- [x] Inserting two Ledger Entries with the same `request_id` throws a unique-constraint exception

## Comments

- Implemented `dev.wallet.domain` entities (`Player`, `Wallet`, `LedgerEntry`, `Direction`,
  `ReasonKind`, `Reason` as an `@Embeddable`) and `dev.wallet.repository` Spring Data interfaces.
  `Reason` is embedded as `reason_kind` / `description` / `reference_id`. `created_at` is set by
  `@PrePersist` when not supplied. `wallet.version` is mapped as a plain column (optimistic locking
  via `@Version` deferred to the concurrent-debit decision in ticket 11).
- `@DataJpaTest` runs the Flyway migration against embedded H2; the duplicate `request_id` test
  asserts `DataIntegrityViolationException` from the DB unique constraint.
- Tests: `src/test/java/dev/wallet/repository/JpaMappingTest.java` (4 tests, green).