# 08: Refund endpoint

**What to build:** `POST /players/{playerId}/wallet/refund` with body `{ amount, requestId, reason, originalDebitId }` where `originalDebitId` is the original Debit's Ledger Entry id. A Refund is a one-shot Credit: it restores the amount, is never subject to the Overdraft Guard, and is refused with `409` when the Debit has already been refunded. Because `requestId` is unique in the database, this ticket must also handle the Duplicate Submission of the same Refund: a retry returns `200` with the original result instead of surfacing a database error.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** ready-for-agent

- [ ] A Refund restores the amount to the Balance and appends exactly one Ledger Entry
- [ ] Refunding the same Debit a second time returns `409` and the Balance is unchanged
- [ ] A Refund larger than the current Balance still succeeds (no Overdraft Guard)
- [ ] Retrying the same Refund `requestId` returns `200` with the original result and applies only once