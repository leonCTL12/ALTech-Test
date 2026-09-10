# 05: Credit endpoint

**What to build:** `POST /players/{playerId}/wallet/credit` with body `{ amount, requestId, reason }`. It validates the input, inserts one Ledger Entry and updates the Wallet Balance in the same transaction, and returns `200` with the new Balance. A request for an unknown player returns `404`. Negative or zero amounts and a missing Idempotency Key return `400`. The Wallet already exists from player creation — no lazy creation logic is needed.

**Blocked by:** 01 Minor Units money conversion, 04 Create player endpoint

**Status:** ready-for-agent

- [ ] A credit increases the Wallet Balance by the amount and appends exactly one Ledger Entry with the correct Reason
- [ ] The returned Balance reflects the credit
- [ ] Unknown player returns `404`
- [ ] Negative/zero amount or missing `requestId` returns `400`
- [ ] Two sequential credits each append one entry and add up correctly