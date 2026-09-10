# 11: Concurrent debit safety test

**What to build:** the proof that two Debits racing against the same Wallet leave the Balance correct and never negative. Two threads each debit an amount that could only both succeed if the guard fails; the final Balance must be exact, non-negative, and both Ledger Entries recorded. Fix production code only if this test uncovers a hole.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** ready-for-agent

- [ ] Two concurrent Debits leave the final Balance exact and never negative
- [ ] At most the affordable Debit succeeds; the overdrawing one is rejected with `409`
- [ ] Both Debits are recorded as Ledger Entries with no lost updates