# 13: README with the five required sections

**What to build:** the reviewer-facing README with the five required sections: how to run the project and tests; design decisions (single-entry ledger with materialized Balance, Money as Minor Units, USD-only); how concurrency and Idempotency are handled (Overdraft Guard conditional update, UNIQUE constraint on the Idempotency Key, atomic transaction); the testing approach, especially the concurrent debit case; and assumptions and limitations (players created in-service rather than external identity, no multi-currency/FX, no auth).

**Blocked by:** 12 OpenAPI/Swagger documentation

**Status:** ready-for-agent

- [ ] How to run: prerequisites, database (H2 default, PostgreSQL profile), and the test command
- [ ] Design decisions section explains the ledger approach and trade-offs
- [ ] Concurrency & Idempotency section explains both mechanisms
- [ ] Testing approach section explains the HTTP-seam strategy and the concurrent debit case
- [ ] Assumptions & limitations section records the known gaps