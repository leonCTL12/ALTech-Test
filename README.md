# Wallet Ledger

A Spring Boot 3.x backend service that safely moves money in and out of player wallets and keeps a
permanent, immutable record of every change — **what happened and why**. Money is handled as whole cents,
the same request is never applied twice, and concurrent operations can never leave a wallet incorrect.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Data JPA · Flyway · H2 (default) / PostgreSQL · springdoc OpenAPI

---

## 1. How to run

### Prerequisites

- **Java 21+** (the project targets Java 21; a JDK is required to build and run)
- **Maven** — via the checked-in wrapper `./mvnw`, no separate install needed
- **Docker** (only for the PostgreSQL profile)

### Run with the default database (H2, in-memory)

```bash
./mvnw spring-boot:run
```

The default profile runs on **in-memory H2**: no database setup, zero configuration — but the data is
**lost when the process stops**. Use it to demo the API; use the PostgreSQL profile below for persistence.

The service starts on `http://localhost:8080`.

### Run with PostgreSQL (persistent)

```bash
docker compose up -d      # starts PostgreSQL 16 on localhost:5432 (db/user/pass: wallet)
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

The schema is owned by Flyway in both cases — the same migration
(`src/main/resources/db/migration/V1__create_schema.sql`) runs on H2 and PostgreSQL, so the two
configurations are drop-in equivalents.

### Run the tests

```bash
./mvnw test                          # full suite against in-memory H2
SPRING_PROFILES_ACTIVE=postgres ./mvnw test   # same suite against PostgreSQL (docker compose up first)
```

Both runs are green: **73 tests, 0 failures**, covering the money paths, concurrency, idempotency,
refunds, history paging, schema, and OpenAPI output.

### Interactive API docs

With the service running, open **http://localhost:8080/swagger-ui.html** for the interactive OpenAPI/Swagger
documentation of every endpoint.

### API at a glance

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| `POST` | `/players` | Create a player with a wallet (0 balance) | `201` | `400` |
| `GET` | `/players/{id}/wallet` | Current balance | `200` | `400` `404` |
| `POST` | `/players/{id}/wallet/credit` | Add value | `200` | `400` `404` |
| `POST` | `/players/{id}/wallet/debit` | Spend value | `200` | `400` `404` `409`¹ |
| `POST` | `/players/{id}/wallet/refund` | Reverse a prior debit | `200` | `400` `404` `409`² |
| `GET` | `/players/{id}/wallet/transactions` | Paged history (cursor `after`/`limit`) | `200` | `400` `404` |

¹ `409 insufficient_balance` — the debit would make the balance negative.  
² `409 already_refunded` — the debit has already been refunded.

```bash
curl -X POST http://localhost:8080/players \
  -H 'Content-Type: application/json' -d '{}'
# {"playerId":1}

curl -X POST http://localhost:8080/players/1/wallet/credit \
  -H 'Content-Type: application/json' \
  -d '{"amount":"10.00","requestId":"b4a1f2c0-8d3e-4a5b-9c6d-0e1f2a3b4c5d",
       "reason":{"reasonKind":"MISSION_REWARD","description":"Completed level 3"}}'
# {"balance":"10.00"}

curl -X POST http://localhost:8080/players/1/wallet/debit \
  -H 'Content-Type: application/json' \
  -d '{"amount":"4.00","requestId":"e3f9a2b1-7c4d-4a8e-9f6b-1c2d3e4f5a6d",
       "reason":{"reasonKind":"PURCHASE","description":"Sword of +3"}}'
# {"balance":"6.00"}

# Refund reverses a debit by its ledger entry id — the amount is always the debit's exact amount,
# so no amount is accepted here; the reasonKind is always REFUND, so only a description is sent.
curl -X POST http://localhost:8080/players/1/wallet/refund \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"c7e5a1b2-9d4e-4f6a-8c1b-2d3e4f5a6b7c",
       "description":"Refund of purchase",
       "originalLedgerEntryId":2}'
# {"balance":"10.00"}
```

---

## 2. Design decisions

### Single-entry append-only ledger with a materialized balance

Every balance change appends **one immutable ledger entry** and updates the wallet's balance column **in the
same database transaction** — a checkbook register, not an account-balance calculator.

The alternative, **double-entry bookkeeping** (what banks and fintech use), records every movement *twice* —
a debit in one account and an equal credit in a contra-account — so that total debits = total credits always,
and any lost money unbalances the books. We deliberately chose **single-entry** because this service is one
account that only takes money in and out; there is no second account to protect, so a contra-row per change
would add zero information. What we gave up is the built-in self-audit: the balance could in principle drift
from its history with nothing noticing — which is exactly why *Balance Check* is our first suggested future
improvement (§6).

> Trade-offs and alternatives: [`docs/adr/0001-single-entry-ledger.md`](docs/adr/0001-single-entry-ledger.md)

### Money as whole minor units

All amounts are stored and computed as a **`long` of minor units** (US cents). The API accepts and returns
**decimal strings** (`"10.00"`); conversion happens only at the boundary, and no floating point exists
anywhere in the domain. A decimal like `1.5` (not a whole number of cents) is rejected.

> [`docs/adr/0002-money-as-minor-units.md`](docs/adr/0002-money-as-minor-units.md)

### The reason is embedded in the ledger entry

Each entry records *what happened and why* inline — a `reasonKind` (`MISSION_REWARD`, `PURCHASE`, `ADMIN`,
`REFUND`) and a free-form description — rather than referencing a separate `reason` table. This is
deliberately **anti-normalized**: if the reason lived in its own table, updating its text later would
silently rewrite history. Embedding freezes the explanation at the moment of the change, so every row is
permanently self-contained.

> [`docs/adr/0006-reason-as-value-object.md`](docs/adr/0006-reason-as-value-object.md)

### Supporting decisions

- **A refund is a new credit that reverses a prior debit** — it never touches the original entry (the
  ledger is immutable), restores **exactly the original debit's amount** (the client cannot choose it), and is
  **never subject to the overdraft guard**; a debit can be refunded only once.
  [`docs/adr/0004-refund-semantics.md`](docs/adr/0004-refund-semantics.md)
- **A player and its wallet are created together** by `POST /players` — no lazy wallet creation, so the
  first money movement can never race a wallet into existence.
  [`docs/adr/0005-lazy-wallet-creation.md`](docs/adr/0005-lazy-wallet-creation.md)
- **History is ordered by entry id** (not timestamp) so cursor pagination is stable under concurrent writes.
  [`docs/adr/0007-history-ordered-by-entry-id.md`](docs/adr/0007-history-ordered-by-entry-id.md)

### Error contract

Errors return a structured body from a global exception handler: `{ "code", "message", "field" }`.
Status codes: `400` invalid input · `404` missing player or debit · `409` insufficient balance /
already refunded. A **duplicate submission returns `200`** with the wallet's current balance — see §3.

---

## 3. Concurrency & Idempotency

### Two senses of idempotency

1. **Effect idempotency** — replaying a request has no extra effect (never applied twice). We keep this
   **fully**, guaranteed by a **UNIQUE constraint on the idempotency key** in the database; a concurrent
   duplicate is never a conflict. This is the part that protects money.
2. **Response idempotency** — a replay returns the *same response body* as the first call (Stripe-style
   behaviour). We deliberately do **not** hold this: a replay returns `200` with the wallet's *true current
   balance*, which may differ from the original response if other operations ran in between.

**In one line: we hold effect idempotency and relax response idempotency, on purpose.**

**Why:** the POST echo's practical job is "where does the wallet stand now"; `GET /wallet` is the
authoritative read, and the ledger records what each request did. Honouring response idempotency would mean
echoing a stale historical balance — a worse lie for a caller's UI than a truthful current balance. The
assignment only requires "the same request must not be applied more than once", which we guarantee
unconditionally.

**The short example:**

1. `debit 4.00` `requestId=R1` → balance 10.00 → 6.00, response `{"balance":"6.00"}` (response lost in transit)
2. `debit 4.00` `requestId=R2` → balance 6.00 → 2.00 (a second, different purchase)
3. Client retries R1 → we return `200 {"balance":"2.00"}` — the true current balance

A textbook response-idempotent service would replay `{"balance":"6.00"}`. We return the real `2.00`. R1 was
still applied exactly once — exactly one ledger entry for R1 — the response just answers "where does the
wallet stand", and the ledger answers "what did R1 do".

### Where the guarantees live: the database, not application checks

The safety properties are **structural**, not enforced by if-checks in the service layer:

- **Idempotency** — the ledger entry's `requestId` is `UNIQUE` per wallet (`wallet_id, request_id`). Two
  identical requests for the same wallet cannot both insert, because the database itself rejects the second.
  An application pre-check can never be the final guard: two threads can both pass it and then race. The
  per-wallet scope also means one player's key can never swallow another player's operation.
- **Concurrency / overdraft guard** — a debit is a single guarded update
  `UPDATE wallet SET balance = balance - :amount WHERE id = :id AND balance >= :amount`. The row is locked
  by the update, so two simultaneous debits serialize; whichever arrives second sees the post-first balance
  and is rejected with `409` if it would overdraw. No lost update is possible.
- **Atomicity** — the ledger entry insert and the balance update share one database transaction; any failure
  rolls back both, so a wallet can never record a change it didn't apply (or vice versa).

> [`docs/adr/0003-idempotency-key.md`](docs/adr/0003-idempotency-key.md)

---

## 4. Testing approach

### One seam: the HTTP API

All tests are `@SpringBootTest` + `MockMvc` against the **real** database (in-memory H2 for the default
suite, PostgreSQL under the `postgres` profile). There are **no service or repository mocks**. This is the
highest seam that still crosses the transaction/database boundary — which is where money-safety bugs actually
live. A mocked repository cannot prove concurrency safety, so we never use one.

Good tests assert **external behaviour**: HTTP status, response body, resulting balance, ledger entry count —
never internal method calls or mock interactions. A money test proves the *end state* is correct.

### The concurrent debit case

The concurrent tests fire the HTTP endpoint from a real thread pool behind a `CyclicBarrier`, so all requests
are released onto the stack at the same moment:

- **Two debits that would each overdraw** a 10.00 wallet by 6.00 → exactly one `200` (balance `4.00`) and
  one `409 insufficient_balance`; final balance 4.00, exactly one debit entry.
- **Two affordable debits** (3.00 and 4.00 against 10.00) → both `200`, final balance 3.00, both entries
  recorded — proving no lost update.
- **Eight concurrent debits** against a 5.00 wallet → exactly 5 succeed, 3 are rejected, final balance 0,
  exactly 5 entries — proving the guard is exact under pressure.

The same pattern covers idempotency: two concurrent **identical** requests (same `requestId`) leave exactly
one ledger entry and both return `200` with the current balance.

`src/test/java/dev/wallet/api/WalletConcurrentDebitControllerTest.java` and
`WalletIdempotencyControllerTest.java` are the proofs.

### Coverage

73 tests across 13 classes: credit, debit, overdraft rejection, idempotent replay, concurrent debits,
concurrent identical requests, refunds (including double-refund), balance, paged history, unknown players,
invalid inputs, schema/Flyway, JPA mapping, and OpenAPI output.

---

## 5. Assumptions & limitations

### Assumptions

- **Players are created in-service.** In production a player would already exist in an external identity
  system; here the repo owns player creation so the service is self-contained and demoable.
- **The system is USD-only.** All amounts are US cents; there is no currency field, no FX, and no
  multi-currency wallet.
- **No authentication or authorization.** Any caller can move any wallet's money; the API is meant for
  demonstration, not exposure.

### Known limitations

- **The default H2 profile is in-memory** — data is lost on restart. Use the PostgreSQL profile
  (`docker compose up -d`) for persistence.
- **A debit can be refunded only once, and only in full.** A refund always restores the original debit's
  exact amount — partial refunds are out of scope; a second refund of the same debit is rejected (`409`).
- **No balance reconciliation today.** Nothing verifies that a wallet's balance still equals the sum of its
  history (see the single-entry trade-off in §2). This is the natural first future improvement.

### Future improvements

1. **Balance Check** — an on-demand or scheduled reconciliation that re-derives each wallet from its ledger
   history and reports any drift (the self-audit property single-entry gave up).
2. **Reservations / funds-on-hold** — set money aside for a bid or purchase without moving it, exposing a
   *ledger balance* distinct from an *available balance*; the one optional product feature that would change
   the money model rather than just adding an endpoint.
3. **Event publishing** — notify other services when a wallet balance changes (the assignment's "domain
   events" practice).
4. **Redis read-cache for `getBalance`** — deferred: a single primary-key read does not justify the
   invalidation complexity at this scale.

---

## 6. How this was built

This project was produced with an **agentic-coding workflow** structured around
[Matt Pocock's skills](https://github.com/mattpocock/skills) — a library of composable, specialist LLM
behaviours that replace random prompting with disciplined, reviewable steps. The human chose which skill
to invoke at each stage and retained final authority over design decisions and verification.

1. **`grill-with-docs`** — a structured design interview (an LLM grills the author on decisions until the
    domain model is unambiguous) produced the decisions in [`docs/adr/`](docs/adr/), one per hard-to-reverse
    choice, and the canonical glossary in [`CONTEXT.md`](CONTEXT.md).
2. **`to-tickets`** — the spec was broken into numbered implementation tickets under
    [`scratch/wallet-ledger/`](scratch/wallet-ledger/), each with blocking edges, so every change was a
    reviewable unit rather than a freeform diff.
3. **`implement` (with `tdd`)** — the agent implemented each ticket test-first against the real database;
    the human **reviewed every test's end-state assertions** — the money paths — and verified the suite
    before each ticket was accepted.

I owned every design decision and the verification — an AI agent handled the mechanical implementation while I acted as the quality gate, reading the tests to confirm the code actually does what it claims rather than writing it by hand.

The committed artifacts are the receipts: `docs/adr/` for decisions, `scratch/wallet-ledger/` for the work
breakdown, and `src/test/` for the proofs.