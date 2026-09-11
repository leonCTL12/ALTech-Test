# 07: Debit endpoint with Overdraft Guard

**What to build:** `POST /players/{playerId}/wallet/debit` with body `{ amount, requestId, reason }`. It applies the Overdraft Guard as a conditional update inside the write transaction: the Balance is only decreased when it is at least the amount. A Debit that would make the Balance negative is rejected with `409` and the Balance is unchanged. Unknown player returns `404`; invalid input returns `400`.

**Blocked by:** 06 Get balance endpoint

**Status:** done

- [x] A Debit decreases the Balance by the amount and appends exactly one Ledger Entry
- [x] A Debit larger than the Balance returns `409` and the Balance is unchanged
- [x] A Debit exactly equal to the Balance succeeds and leaves `"0.00"`
- [x] Unknown player returns `404`

## Comments

- Implemented `POST /players/{playerId}/wallet/debit` on `dev.wallet.api.WalletController` (a
  `@PostMapping("/debit")` delegating to `dev.wallet.service.WalletService#debit(long, MinorUnits, Reason, String)`),
  returning the new Balance as a decimal string in a `BalanceResponse(String balance)` record — the same
  `{ "balance": "..." }` shape credit and balance return. The request body record was generalised from
  `CreditRequest` to `AmountRequest` since credit and debit share the same shape.
- The Overdraft Guard is a **conditional update** inside the write transaction:
  `WalletRepository#debitIfSufficient` runs `UPDATE wallet SET balance = balance - :amount WHERE id = :id
  AND balance >= :amount`; if it affects 0 rows the debit is rejected with `409 insufficient_balance` and the
  transaction rolls back, leaving the Balance and ledger untouched. The wallet is read via the locking
  `findByPlayerIdForUpdate` first (same concurrency seam as credit), so the in-memory Balance is authoritative
  and the `wallet.debit(...)` mutation agrees with the DB value. The Debit Ledger Entry is appended in the same
  transaction as the conditional update, so a failed guard leaves no entry.
- Unknown player → `404 player_not_found`; non-positive path id → `400 invalid_path`; missing/invalid body
  fields → `400` (shared `require*` helpers); negative or zero amount → `400 invalid_amount` via `toMinorUnits`.
- Tests: `src/test/java/dev/wallet/api/WalletDebitControllerTest.java` (5 tests) at the spec's single seam
  (`@SpringBootTest` + MockMvc + real H2): debit decreases balance + one DEBIT entry with reason, debit larger
  than balance → `409` + unchanged, debit exactly equal to balance → `"0.00"`, unknown player → `404`,
  negative/zero amount → `400` + unchanged. Full suite 39 green.