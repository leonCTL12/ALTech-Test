# 0007: History ordered by Ledger Entry id, never created_at

`GET /players/{playerId}/wallet/transactions` pages a wallet's history newest first, ordered by the
Ledger Entry's auto-increment `id` descending and keyed by `after=<entryId>` (strictly older than the
cursor, `id < after`). We rejected ordering by `created_at` because a cursor on a timestamp is not stable:
two entries can share the same `created_at` (same `Instant.now()` tick) or be reordered by clock skew, so a
page boundary could skip or duplicate entries. The `id` is assigned by the database at insert time, is
monotonically increasing, and is unique — a total order that never ties.

This also makes paging correct under concurrency without extra machinery: because the ledger is
append-only (entries are never updated or deleted), each page only reads entries strictly older than the
cursor. An entry that was already returned can therefore never be returned again (no duplicates), and every
entry older than the cursor is reached exactly once (no gaps). An entry inserted between two page reads
carries an id newer than the cursor and only surfaces on a fresh first page — the standard, accepted
behaviour of any cursor-based API, not a guarantee this design makes.