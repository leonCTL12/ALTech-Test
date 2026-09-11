# 05: Credit endpoint

**What to build:** `POST /players/{playerId}/wallet/credit` with body `{ amount, requestId, reason }`. It validates the input, inserts one Ledger Entry and updates the Wallet Balance in the same transaction, and returns `200` with the new Balance. A request for an unknown player returns `404`. Negative or zero amounts and a missing Idempotency Key return `400`. The Wallet already exists from player creation — no lazy creation logic is needed.

**Blocked by:** 01 Minor Units money conversion, 04 Create player endpoint

**Status:** done

- [x] A credit increases the Wallet Balance by the amount and appends exactly one Ledger Entry with the correct Reason
- [x] The returned Balance reflects the credit
- [x] Unknown player returns `404`
- [x] Negative/zero amount or missing `requestId` returns `400`
- [x] Two sequential credits each append one entry and add up correctly

## Comments

- Implemented `POST /players/{playerId}/wallet/credit`: `dev.wallet.api.WalletController`
  delegates to `dev.wallet.service.WalletService#credit(...)`, which is `@Transactional` and
  **inserts the Ledger Entry + mutates the Wallet Balance in one transaction** (single-entry
  ledger, ADR-0001). Unknown player → `404 player_not_found`; negative/zero amount or a missing
  `requestId`/`reason` → `400`; non-positive path id → `400 invalid_path`.
- **Concurrency:** the wallet row is read with a pessimistic write lock
  (`@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`) inside the write transaction
  (ADR-0001 rationale). Two concurrent credits serialize and both apply — no lost update.
  This is the pattern chosen over an atomic SQL increment to keep entity code natural;
  ticket 07's Overdraft Guard still uses the conditional-update style for Debit.
- **Money:** `amount` arrives as a decimal string and is converted to Minor Units at the
  controller boundary (`MinorUnits.fromDecimal`); `IllegalArgumentException` (bad format,
  non-positive) maps to `400 invalid_amount`. Responses return the Balance via
  `MinorUnits.format(long)` (delegated from `toDecimalString`), e.g. `"10.00"`.
- **Reason** is a structured `{ reasonKind, description, referenceId? }` stored as the
  `@Embeddable` Reason value object (ADR-0006); `referenceId` is currently unused by credit.
- **Idempotency (deferred to ticket 10):** the schema/entity UNIQUE constraint on
  `request_id` already prevents double-application; today a *duplicate* `requestId` surfaces
  as a `500 internal_error` because the duplicate-submission → `200 original result` handling
  is ticket 10's scope. `requestId` is the wire field name per the API contract in spec.md.
- **Tests:** `src/test/java/dev/wallet/api/WalletCreditControllerTest.java` (5 tests). The
  spec's single seam (`@SpringBootTest` + MockMvc + real H2) is used; assertions are scoped
  per wallet via the new `LedgerEntryRepository#findByWalletIdOrderByIdAsc` (also needed by
  ticket 09's history endpoint). Full suite 32 green.