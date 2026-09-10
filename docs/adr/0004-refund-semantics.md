# 0004: Refunds are one-shot and unguarded

A refund reverses a prior debit and may only be issued **once per debit**: it records the original
debit's id, and a second refund of the same debit is rejected. Because a refund is a credit, it always
succeeds and is **not** subject to the overdraft guard — a refund adds its full amount regardless of
balance. We rejected chained refunds (refunding a refund) as a case that "makes no sense" for a wallet
and complicates the model with no real use.