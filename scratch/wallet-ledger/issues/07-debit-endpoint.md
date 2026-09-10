# 07: Debit endpoint with Overdraft Guard

**What to build:** `POST /players/{playerId}/wallet/debit` with body `{ amount, requestId, reason }`. It applies the Overdraft Guard as a conditional update inside the write transaction: the Balance is only decreased when it is at least the amount. A Debit that would make the Balance negative is rejected with `409` and the Balance is unchanged. Unknown player returns `404`; invalid input returns `400`.

**Blocked by:** 06 Get balance endpoint

**Status:** ready-for-agent

- [ ] A Debit decreases the Balance by the amount and appends exactly one Ledger Entry
- [ ] A Debit larger than the Balance returns `409` and the Balance is unchanged
- [ ] A Debit exactly equal to the Balance succeeds and leaves `"0.00"`
- [ ] Unknown player returns `404`