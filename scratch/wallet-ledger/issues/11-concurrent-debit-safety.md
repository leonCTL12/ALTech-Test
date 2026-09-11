# 11: Concurrent debit safety test

**What to build:** the proof that two Debits racing against the same Wallet leave the Balance correct and never negative. Two threads each debit an amount that could only both succeed if the guard fails; the final Balance must be exact, non-negative, and both Ledger Entries recorded. Fix production code only if this test uncovers a hole.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** done

- [x] Two concurrent Debits leave the final Balance exact and never negative
- [x] At most the affordable Debit succeeds; the overdrawing one is rejected with `409`
- [x] Both Debits are recorded as Ledger Entries with no lost updates

## Comments

- **Proof tests** live in `src/test/java/dev/wallet/api/WalletConcurrentDebitControllerTest.java` (3 tests)
  at the spec's single seam (`@SpringBootTest` + MockMvc + real H2), driving the debits through the HTTP
  API with a `CyclicBarrier` + thread pool so both requests start at the same moment.
- **Race test:** two concurrent `6.00` Debits against a `10.00` Wallet — only one can pass the guard.
  Asserts the statuses are exactly one `200` and one `409 insufficient_balance`, the final Balance is the
  exact `4.00` (never negative), and exactly one DEBIT Ledger Entry was recorded. The loser's rejected
  transaction leaves no partial update.
- **No-lost-update test:** two concurrent Debits that are both affordable (`3.00` + `4.00` on `10.00`)
  both return `200`, the Balance reflects *both* deductions (`3.00`), and both DEBIT entries are present —
  proving a naive read-modify-write (where one thread's deduction would be overwritten) is caught.
- **Many-thread test:** eight concurrent `1.00` Debits against a `5.00` Wallet. Exactly five succeed, three
  are rejected with `409`, the Balance is exactly `0.00`, and exactly five DEBIT entries exist — the
  invariant "never negative, no lost update" holds under real contention.
- **No production hole found:** the Overdraft Guard (`WalletRepository#debitIfSufficient`, a conditional
  `UPDATE ... WHERE balance >= :amount` inside the write transaction, after `findByPlayerIdForUpdate`
  takes the pessimistic row lock) already serializes racing Debits on the Wallet row. I verified the tests
  can fail: with the `balance >= :amount` clause temporarily removed, two tests go red (both Debits
  "succeed" and the ledger shows money created). Production code was left unchanged.
- Full suite now 61 green (58 + 3).