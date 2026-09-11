# 0004: Refunds are one-shot, full-reversal, and unguarded

A refund reverses a prior debit and may only be issued **once per debit**: it records the original
debit's id, and a second refund of the same debit is rejected. A refund always restores **exactly the
original debit's amount** — the client names only the debit to undo, never an amount, so a refund can
neither over- nor under-credit. Because a refund is a credit, it always succeeds and is **not** subject
to the overdraft guard; and since the refunded debit already succeeded (the balance covered it), the
refund can never make the balance negative. We rejected chained refunds (refunding a refund) as a case
that "makes no sense" for a wallet and complicates the model with no real use.