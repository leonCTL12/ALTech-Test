# 09: Transaction history pagination

**What to build:** `GET /players/{playerId}/wallet/transactions?after=<entryId>&limit=<n>` returning the Ledger Entries of a player's Wallet, newest first, using cursor pagination: the `after` entry id continues from that point and `limit` caps the page size. Ordering must be stable so a page boundary never skips or duplicates an entry. Unknown player returns `404`.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** done

- [x] Returns entries newest first with the most recent Reason and amounts
- [x] `limit` caps the page size; `after` resumes from the given entry id
- [x] Paging through a mixed history of Credits and Debits returns every entry exactly once with no gaps or duplicates
- [x] Unknown player returns `404`

## Comments

- Implemented `GET /players/{playerId}/wallet/transactions?after=<entryId>&limit=<n>` on
  `dev.wallet.api.WalletController` (a `@GetMapping("/transactions")` delegating to
  `dev.wallet.service.WalletService#history(long, Long, int)`). The response is
  `{ "entries": [ { id, amount, direction, reason { reasonKind, description, referenceId }, createdAt } ], "nextAfter" }`.
  `nextAfter` is the id to pass as the next `after` cursor, or `null` when the end of history is reached.
- **Cursor pagination** is keyset pagination: `after` is exclusive (`id < after`), so passing the last id
  seen never duplicates the boundary entry. To know whether a next page exists, the repository fetches
  `limit + 1` rows and the service trims back, computing `nextAfter` from the last returned id.
- **Stable ordering** comes from ordering by the Ledger Entry `id` (auto-increment identity) descending —
  see `docs/adr/0007-history-ordered-by-entry-id.md`. A timestamp ordering would tie under the same
  `Instant.now()` tick and could skip/duplicate at page boundaries; id order is total and, because the
  ledger is append-only, `id < after` can neither revisit an entry already returned nor skip one older than
  the cursor. (Entries inserted mid-paging are newer than the cursor and only appear on a fresh first page —
  standard cursor behaviour.)
- `limit` defaults to 50 and must be between 1 and 100, else `400 invalid_limit`; `after` must be a
  positive whole number if present, else `400 invalid_cursor`. Both are parsed from raw query-string
  Strings in the controller so a non-numeric value is a clean `400` with the offending field, not a 500
  type-mismatch. Unknown player → `404 player_not_found`.
- Tests: `src/test/java/dev/wallet/api/WalletTransactionHistoryControllerTest.java` (7 tests) at the spec's
  single seam (`@SpringBootTest` + MockMvc + real H2): empty history, unknown player `404`, newest-first
  ordering with reasons and amounts, `limit` caps the page, `after` resumes exclusively from the cursor,
  **paging a mixed Credit/Debit history returns every entry exactly once** (loop on `nextAfter` until `null`
  and compare the concatenated ids to the full descending set), and invalid `limit`/`after` values → `400`.
  Full suite now 54 green.