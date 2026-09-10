# 03: JPA entities and repositories

**What to build:** the JPA entities `Player`, `Wallet`, and `LedgerEntry` mapped to the Flyway schema, plus Spring Data `JpaRepository`s for each. A Wallet belongs to one Player; a Ledger Entry belongs to one Wallet. Inserting a second Ledger Entry with the same Idempotency Key must fail on the database UNIQUE constraint.

**Blocked by:** 02 Flyway schema

**Status:** ready-for-agent

- [ ] A `@DataJpaTest` saves and reads back a Player
- [ ] A Wallet saves and reads back with its Player relationship
- [ ] A Ledger Entry saves and reads back with its Wallet relationship, including the Reason fields
- [ ] Inserting two Ledger Entries with the same `request_id` throws a unique-constraint exception