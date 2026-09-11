# 16: Rename `originalDebitId` to `originalLedgerEntryId` on refund

**What to build:** rename `originalDebitId` across the entire codebase to `originalLedgerEntryId`:
request/response fields, the domain model (`LedgerEntry`), repository names, DB column name, migration,
controller endpoints, service methods, tests, OpenAPI docs, and issue tracker references.

**Blocked by:** none

**Status:** done

- [x] Rename request field `originalDebitId` → `originalLedgerEntryId` in `RefundRequest`
- [x] Rename response field (if exposed) accordingly
- [x] Update `LedgerEntry.originalDebitId` → `LedgerEntry.originalLedgerEntryId`
- [x] Rename repository method `findByOriginalDebitId(…)` → `findByOriginalLedgerEntryId(…)`
- [x] Rename DB column `original_debit_id` → `original_ledger_entry_id` in `V1__create_schema.sql`
- [x] Rename service parameter `originalDebitId` in `WalletService.refund(…)`
- [x] Update all test assertions that use `originalDebitId` as field or variable name
- [x] Update `@ApiResponse` descriptions referencing `originalDebitId` in OpenAPI annotations
- [x] Run full test suite to confirm nothing breaks

## Why rename

The field lives on `LedgerEntry` and stores an ID of type `Long` pointing at another `ledger_entry.id`.
Its semantic meaning — "the original entry being refunded" — is accurate regardless of whether that entry
is technically a debit. The current name implies a constraint ("must be a debit") rather than expressing
the structural relationship (a link to a prior ledger entry). Renaming makes the contract explicit without
changing any behaviour.
