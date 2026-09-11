# 0003: Client-supplied idempotency key enforced by a unique constraint

Every balance-changing request carries a client-generated UUID `requestId`, stored on the ledger entry
under a **UNIQUE constraint**. Re-submission of the same `requestId` is detected by the database and the
request is ignored — the wallet's current balance is returned — so a request is never applied twice even
when two identical requests arrive simultaneously. This deliberately relaxes *response* idempotency (same
request → identical response body); *effect* idempotency is what matters for money and is guaranteed here.
We rejected deriving the key from domain data (e.g. an order id) because a single uniform client key works
across credit, debit, and refund, and lets the database — not app logic — be the final guard against
double-application.
