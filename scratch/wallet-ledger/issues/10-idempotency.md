# 10: Idempotency for repeated and concurrent identical requests

**What to build:** Duplicate Submission handling for Credit and Debit: a request carrying an Idempotency Key the system has already applied is ignored and the original result is returned (`200`, same outcome), never applied twice and never a conflict. This must also hold when two identical requests arrive concurrently — a thread-pool test through the HTTP seam drives both requests at once and asserts exactly one Ledger Entry and one Balance change.

**Blocked by:** 07 Debit endpoint with Overdraft Guard

**Status:** done

- [x] Repeating a Credit `requestId` applies once and returns `200` with the original result
- [x] Repeating a Debit `requestId` applies once and returns `200` with the original result
- [x] Two concurrent identical requests apply once: exactly one Ledger Entry, Balance changed once
- [x] The Balance returned is correct and never reflects a double application

## Comments

- **Duplicate detection** now lives in `dev.wallet.service.WalletService#credit` and `#debit`: before
  applying anything, each checks `ledgerEntries.findByRequestId(requestId)` (the same guard Refund already
  had). When the Idempotency Key is already applied, the request is a Duplicate Submission and the current
  Balance is returned as `200` — never a conflict, never a double application.
- **Why the check is race-proof:** the check runs *inside* the write transaction, *after*
  `findByPlayerIdForUpdate` has taken a pessimistic row lock on the Wallet. Two concurrent identical
  requests serialize on that lock: the loser cannot run its `findByRequestId` read until the winner has
  committed, so it sees the winner's entry and returns the original result. The DB UNIQUE constraint on
  `request_id` (ADR-0003) stays as the final guard for any path that bypasses the lock.
- **Concurrency tests** (`src/test/java/dev/wallet/api/WalletIdempotencyControllerTest.java`, 4 tests) drive
  both requests through the HTTP seam at once with a `CyclicBarrier` + thread pool and assert both return
  `200`, exactly one Ledger Entry exists, and the Balance changed exactly once (and is the correct final
  value) for both Credit and Debit. Full suite now 58 green.
- **Deliberate contract:** a duplicate returns the wallet's *current* Balance (`200`, never a conflict,
  never a double application). This relaxes *response* idempotency (same request → identical response body)
  in favour of answering "where does the wallet stand now"; *effect* idempotency — applied at most once —
  is guaranteed by the UNIQUE constraint. Chosen by the author after discussion; the README ticket (13)
  records the reasoning and interview talking points.