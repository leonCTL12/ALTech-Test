# 0001: Single-entry ledger with a materialized balance

We keep an immutable append-only `LedgerEntry` for every balance change, and store a `balance` column on
the wallet that is updated **in the same DB transaction** as the new entry, using a guarded conditional
update (`SET balance = balance + delta WHERE balance + delta >= 0`). We chose this over pure derivation
(sum of entries) and over double-entry bookkeeping: it gives fast balance reads and a straightforward
overdraft guard, while the ledger still gives a permanent history — the right trade-off at the scale of a
single-currency player wallet. Double-entry was rejected as overkill; pure derivation was rejected because
every read becomes a scan and overdraft-guarding is more awkward.
