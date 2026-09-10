# 10: Idempotency for repeated and concurrent identical requests

**What to build:** Duplicate Submission handling for Credit and Debit: a request carrying an Idempotency Key the system has already applied is ignored and the original result is returned (`200`, same outcome), never applied twice and never a conflict. This must also hold when two identical requests arrive concurrently — a thread-pool test through the HTTP seam drives both requests at once and asserts exactly one Ledger Entry and one Balance change.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** ready-for-agent

- [ ] Repeating a Credit `requestId` applies once and returns `200` with the original result
- [ ] Repeating a Debit `requestId` applies once and returns `200` with the original result
- [ ] Two concurrent identical requests apply once: exactly one Ledger Entry, Balance changed once
- [ ] The Balance returned is correct and never reflects a double application