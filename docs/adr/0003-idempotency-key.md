# 0003: Client-supplied idempotency key enforced by a unique constraint

Every balance-changing request carries a client-generated UUID `requestId`, stored on the ledger entry
under a **UNIQUE constraint scoped to the wallet** (`wallet_id, request_id`). Re-submission of the same
`requestId` for the same wallet is detected by the database and the request is ignored — the wallet's
current balance is returned — so a request is never applied twice even when two identical requests arrive
simultaneously. This deliberately relaxes *response* idempotency (same request → identical response body);
*effect* idempotency is what matters for money and is guaranteed here. We rejected deriving the key from
domain data (e.g. an order id) because a single uniform client key works across credit, debit, and refund,
and lets the database — not app logic — be the final guard against double-application.

Scoping the key per wallet (not globally) keeps that guarantee where it matters — within one wallet — while
letting different wallets use the same key: a documented example key, or a shared client constant, can no
longer make one player's operation silently swallow another player's. A client must still mint a fresh key
per logical operation; reusing a key for a *different* operation on the same wallet is treated as a
duplicate submission (the wallet's current balance is returned).
