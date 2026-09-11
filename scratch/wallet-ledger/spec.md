# Wallet Ledger Core — Spec

## Problem Statement

A game company needs a backend service that moves money in and out of player wallets safely. Every
change must be recorded permanently with *what happened and why*, the same request must never be applied
twice, two simultaneous requests must never corrupt a wallet, and a failed operation must never leave a
partial update. The current repo is only a Spring Boot scaffold — no wallet, ledger, or API exists yet.

## Solution

A Spring Boot 3.x service exposing REST endpoints to Credit, Debit, and Refund a player's Wallet, read
its Balance, and page through its transaction history. Money is stored as whole `long` Minor Units. Every
Balance change appends an immutable Ledger Entry and updates the Wallet's materialized Balance in the same
database transaction. Idempotency is enforced by a client-supplied Idempotency Key under a database UNIQUE
constraint, so a Duplicate Submission is detected by the database itself and the original result returned.
A Debit is rejected by the Overdraft Guard when it would make the Balance negative. A Refund is a one-shot
Credit that reverses a prior Debit. A Player and its Wallet are created together by a dedicated endpoint.
Errors are returned as a structured body from a global exception handler. The schema is managed by Flyway and runs on H2 by default
with PostgreSQL as a drop-in profile.

## User Stories

1. As a player, I want to credit my wallet so that I can add value to it.
2. As a player, I want to debit my wallet so that I can spend value from it.
3. As a player, I want my debit rejected when my balance is insufficient, so that I can never go negative.
4. As a player, I want to read my current balance, so that I know how much value I have.
5. As a player, I want to page through my transaction history, so that I can review what happened to my wallet.
6. As a player, I want each ledger entry to record the reason it happened, so that I can see *what* and *why*.
7. As a player, I want a refund of a debit to add the value back, so that a mistaken purchase is reversible.
8. As a player, I want a refund to be refused if the debit was already refunded, so that I cannot reverse the same debit twice.
9. As an operator, I want a request carrying a repeated idempotency key to be ignored, so that a retry after a network failure does not double-charge.
10. As an operator, I want a repeated request to return `200` with the wallet's true current balance, so that a retry never double-charges and the caller always sees where the wallet stands. (We deliberately relax response idempotency — see Idempotency & concurrency.)
11. As an operator, I want to create a player together with its wallet in one call, so that the service is
    self-contained and demoable without touching the database.
12. As an operator, I want any wallet operation for an unknown player to return 404, so that missing players
    are handled clearly.
13. As an operator, I want invalid inputs (negative amounts, missing fields) rejected with a clear error, so that bad requests fail fast.
14. As an operator, I want a structured error body with a machine-readable code, so that clients can handle failures programmatically.
15. As an operator, I want two concurrent debits to leave the wallet correct and never negative, so that the wallet is safe under load.
16. As an operator, I want two concurrent identical requests to apply only once, so that the wallet is safe under duplicate submission.
17. As an operator, I want the ledger to be immutable, so that history is permanent and auditable.
18. As an operator, I want the balance and its ledger entry to change atomically, so that a failure never leaves a partial update.
19. As an operator, I want the API documented by OpenAPI/Swagger, so that clients can discover the contract.
20. As an operator, I want a refund to be a credit that is never subject to the overdraft guard, so that a refund always restores its full amount.

## Implementation Decisions

### Architecture & modules

- **Single Spring Boot application**, layered: `controller` (REST + validation), `service` (domain rules,
  transactions), `repository` (JPA), `domain` (entities + value objects). No separate modules — one
  deployable unit is right for this scale.
- **Single-entry append-only ledger with a materialized balance** (ADR-0001). Each Credit/Debit/Refund
  inserts one immutable Ledger Entry and updates the Wallet's Balance column in the same transaction.
- **Money as `long` Minor Units** (ADR-0002). API accepts/returns decimal strings; conversion happens only
  at the boundary. No `BigDecimal` or floating point in the domain.
- **Single currency: USD throughout** (grilling decision revisited). The whole system is USD; amounts are
  US cents. No currency field, no FX, no conversion. (Multi-currency wallets and FX are explicitly out of
  scope — see Out of Scope.)

### API contract

- `POST /players` — body: `{}` (empty) — creates a Player and its Wallet (0 balance, USD) in one
  transaction; returns the generated `playerId`. No Idempotency Key: it is not a money movement.
- `POST /players/{playerId}/wallet/credit` — body: `{ amount, requestId, reason }`
- `POST /players/{playerId}/wallet/debit` — body: `{ amount, requestId, reason }`
- `POST /players/{playerId}/wallet/refund` — body: `{ requestId, description, originalLedgerEntryId }`
- `GET /players/{playerId}/wallet` — returns current Balance
- `GET /players/{playerId}/wallet/transactions?after=<entryId>&limit=<n>` — cursor-paginated history,
  newest first
- `amount` is a decimal string (e.g. `"10.00"`) converted to US cents at the boundary; must be a positive
  whole number of cents after conversion.
- `requestId` is a client-supplied UUID (the Idempotency Key).
- `reason` is structured: a `reasonKind` enum (`MISSION_REWARD`, `PURCHASE`, `ADMIN`, `REFUND`) and a
  human `description`. Credit and Debit carry a full `reason`; a Refund carries only a free-form
  `description` because its ReasonKind is always `REFUND` — the server sets it, the client cannot choose.
  The link back to the original Debit's Ledger Entry id is carried by `originalLedgerEntryId`,
  not by the reason.

### Idempotency & concurrency

- **Idempotency Key = client UUID, enforced by a UNIQUE constraint** on the Ledger Entry (ADR-0003). The
  database is the final guard: two simultaneous identical requests cannot both insert.
- **Duplicate Submission is ignored for money and answered with the wallet's current Balance** (`200`,
  never a conflict). This deliberately relaxes *response* idempotency (same request → identical response
  body): a replay returns the true current Balance, which may differ from the first response if other
  operations ran in between. *Effect* idempotency — the same request is never applied twice — is guaranteed
  by the UNIQUE constraint. The ledger, not the POST echo, is the source of truth for what a request did.
- **Overdraft Guard** implemented as a conditional update in the write transaction:
  `UPDATE wallet SET balance = balance - :amount WHERE id = :id AND balance >= :amount`. If zero rows are
  affected, the Debit is rejected (409). This is atomic under concurrency because the row is locked by the
  update.
- **Atomicity**: Ledger Entry insert + Balance update share one `@Transactional` boundary; a failure rolls
  back both.

### Refund semantics

- **One Refund per Debit** (ADR-0004): the Refund records the original Debit's Ledger Entry id; a second
  Refund of the same Debit is rejected (409). Enforced by a UNIQUE constraint on the refund's
  `originalLedgerEntryId` plus an application check.
- **A Refund restores exactly the original Debit's amount** — the client names the Debit to undo
  (`originalLedgerEntryId`), never an amount. The Refund's amount is read from the original entry, so it
  can neither over- nor under-credit.
- A Refund is a **Credit**: it always succeeds and is never subject to the Overdraft Guard. Because the
  refunded Debit already succeeded (the balance covered it), the refund can never make the balance negative.

### Player & wallet lifecycle

- **Player and Wallet are created together** by `POST /players` in one transaction (ADR-0005). A player
  always has exactly one Wallet, starting at 0 balance. No lazy creation: the earlier "insert wallet if
  absent" race on first Credit is gone because the Wallet exists before any money movement.
- **Wallet operations for an unknown player → 404.** (The no-Wallet case is unreachable through the API
  since the Wallet is created with the Player.)
- **Known divergence from a real deployment:** in production a player would already exist in an external
  identity system; the repo owns player creation so the service is self-contained and demoable.

### Error contract

- Global `@RestControllerAdvice` returning `{ "code": "...", "message": "...", "field": "..." }`.
- Statuses: `400` invalid input, `404` missing player/debit, `409` insufficient balance or
  already-refunded debit. Duplicate Submission returns `200` with the wallet's current balance.

### Database

- **Flyway migrations** for the schema (portable SQL).
- **H2 by default** (in-memory for tests, file or in-memory for run); **PostgreSQL profile** as a drop-in
  (ADR not needed — same SQL).
- Schema: `player` (id), `wallet` (id, player_id, balance, version), `ledger_entry`
  (id, wallet_id, amount, direction, reason_kind, description, request_id UNIQUE,
  original_ledger_entry_id UNIQUE NULL, created_at).

### Observability & docs

- **OpenAPI/Swagger** via springdoc.
- **Actuator** already wired (health, metrics) — keep.
- **No Redis** (grilling decision: premature for a single PK read; no benchmark justifies it).
- **No k6 load script** (grilling decision: spec requires in-repo concurrent tests, not a load harness).

## Testing Decisions

- **One seam: the HTTP API.** All tests are `@SpringBootTest` + `MockMvc` against the real H2 database —
  no service or repository mocks. This is the highest seam that still exercises the transaction/DB
  boundary where money-safety bugs actually live. A mocked repository cannot prove concurrency safety.
- **What makes a good test:** assert external behavior (HTTP status, response body, resulting Balance,
  Ledger Entry count) — never internal method calls or mock interactions. A good money test proves the
  *end state* is correct.
- **Modules tested:** the whole application through the API seam. Pure-logic unit tests are added only if
  they earn their place (e.g. minor-unit conversion at the boundary); the default is the single seam.
- **Required tests:**
  - Credit increases Balance and appends one Ledger Entry with the correct Reason.
  - Debit decreases Balance and appends one Ledger Entry.
  - Debit that would overdraw is rejected with 409 and Balance is unchanged.
  - Repeated submission of the same requestId applies once, is never a conflict, and returns `200` with the
    current balance.
  - Two concurrent identical requests apply once (thread pool through the API seam).
  - Two concurrent debits leave the final Balance correct and never negative.
  - Refund restores the amount, is refused a second time (409), and is not subject to the overdraft guard.
  - `POST /players` creates a Player + Wallet; wallet operations for an unknown player return 404.
  - Invalid inputs (negative amount, missing requestId) return 400.
  - Transaction history pages via cursor with stable ordering.
- **Prior art:** none yet — this is the first test suite in the repo. The existing
  `WalletLedgerApplicationTests` context-load test is the starting point.

## Out of Scope

- Redis caching for `getBalance` (explicitly deferred).
- k6 / standalone load-test harness.
- Multi-currency wallets, FX, and any currency field — the system is USD-only.
- Daily login streak, promotional rewards, player-to-player transfers, reservations, bulk reward
  distribution, claim reward.
- Domain events / event publishing.
- WebSocket.
- Authentication / authorization.
- Production deployment (Docker Compose optional; not required for the take-home).

## Further Notes

- The README is the primary reviewer-facing artifact and must contain the five required sections:
  how to run, design decisions, concurrency & idempotency, testing approach (especially the concurrent
  debit case), and assumptions & limitations.
- The author is learning Java/Spring from a C# background; implementation should teach concepts
  (transactions, JPA, beans, Flyway) as it goes, but must not lower engineering standards.
- The `.context/` files and this spec's source are private and must never appear in the submitted repo.
- The `scratch/` tracker and `docs/` are committed so reviewers can see how the work was divided.