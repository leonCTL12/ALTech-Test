# 09: Transaction history pagination

**What to build:** `GET /players/{playerId}/wallet/transactions?after=<entryId>&limit=<n>` returning the Ledger Entries of a player's Wallet, newest first, using cursor pagination: the `after` entry id continues from that point and `limit` caps the page size. Ordering must be stable so a page boundary never skips or duplicates an entry. Unknown player returns `404`.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** ready-for-agent

- [ ] Returns entries newest first with the most recent Reason and amounts
- [ ] `limit` caps the page size; `after` resumes from the given entry id
- [ ] Paging through a mixed history of Credits and Debits returns every entry exactly once with no gaps or duplicates
- [ ] Unknown player returns `404`