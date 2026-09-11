# 0006: Reason embedded, not a separate table

A ledger entry stores its Reason (kind, description) directly as columns within
the entry's row via an `@Embeddable`, never as a separate table. We rejected normalizing Reason into
its own table (e.g. `reason(id, kind, description)` referenced by FK) for two reasons:

- **Immutability / auditability.** Every balance change records *what happened and why* at the moment it
  occurred. If Reason lived in a separate table, updating the text later would silently rewrite history,
  destroying the permanent record. Embedding freezes the reason inline so each row is fully self-contained
  and cannot drift.
- **No sharing.** Each reason belongs to exactly one ledger entry; nothing is reused. A separate table adds a
  join and a lookup column for zero storage benefit.

This goes against classic relational normalization (which optimizes for shared mutable facts stored once),
but fits the domain: a money-moving ledger is append-only and auditable, not a live catalog of values.
