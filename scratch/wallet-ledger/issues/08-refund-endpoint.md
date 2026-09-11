# 08: Refund endpoint

**What to build:** `POST /players/{playerId}/wallet/refund` with body `{ amount, requestId, reason, originalLedgerEntryId }` where `originalLedgerEntryId` is the original Debit's Ledger Entry id. A Refund is a one-shot Credit: it restores the amount, is never subject to the Overdraft Guard, and is refused with `409` when the Debit has already been refunded. Because `requestId` is unique in the database, this ticket must also handle the Duplicate Submission of the same Refund: a retry returns `200` with the original result instead of surfacing a database error.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** done

- [x] A Refund restores the amount to the Balance and appends exactly one Ledger Entry
- [x] Refunding the same Debit a second time returns `409` and the Balance is unchanged
- [x] A Refund larger than the current Balance still succeeds (no Overdraft Guard)
- [x] Retrying the same Refund `requestId` returns `200` with the original result and applies only once

## Comments

- Implemented `POST /players/{playerId}/wallet/refund` on `dev.wallet.api.WalletController` (a
  `@PostMapping("/refund")` delegating to `dev.wallet.service.WalletService#refund(long, MinorUnits, Reason,
  String, Long)`), returning the new Balance as a decimal string in the same `BalanceResponse` shape the other
  wallet endpoints use. The request body is a `RefundRequest(String amount, String requestId, ReasonInput
  reason, Long originalLedgerEntryId)` record.
- **One-shot semantics** are enforced in the write transaction, which first takes the pessimistic write lock on
  the wallet (`findByPlayerIdForUpdate`) so per-player refunds serialize. Inside, the checks run in this order:
  1. **Duplicate Submission** — if a Ledger Entry with the same `requestId` already exists, return `200` with the
     current Balance (the original result) and apply nothing. This is checked *first* so a retry is transparently
     idempotent even though the debit is by then already refunded.
  2. **Already refunded** — if any entry already references the same `originalLedgerEntryId`, throw `409 already_refunded`.
  3. **Valid original debit** — the `originalLedgerEntryId` must exist and be a `DEBIT` entry, else `404 debit_not_found`.
  Then it appends a single `CREDIT` Ledger Entry (with `originalLedgerEntryId` set and `ReasonKind.REFUND`) and credits
  the Wallet in the same transaction. Because a Refund is a Credit, it is **never** subject to the Overdraft Guard.
- The DB UNIQUE constraints on `request_id` and `original_ledger_entry_id` (V1 schema) remain the final safety net
  behind the application checks, which is what makes the Duplicate Submission and one-shot rules hold under
  concurrency.
- Unknown player → `404 player_not_found`; missing or non-positive `originalLedgerEntryId` → `400`; missing/invalid
  body fields → `400` via the shared `require*` helpers. The controller reuses one `parse(...)` helper across
  credit/debit/refund (the refund call passes the extra `originalLedgerEntryId` on the side), so the amount/reason
  validation is not duplicated.
- Tests: `src/test/java/dev/wallet/api/WalletRefundControllerTest.java` (8 tests) at the spec's single seam
  (`@SpringBootTest` + MockMvc + real H2): refund restores amount + one CREDIT entry with the debit reference,
  second refund of same debit → `409` + unchanged, refund larger than balance succeeds, retry of same `requestId`
  → `200` + applies once, **two concurrent refunds of the same debit apply only once** (one `200` + one `409`,
  single refund entry), unknown player → `404`, unknown original debit → `404`, missing `originalLedgerEntryId` → `400`.
  Full suite now 47 green.