# Wallet Ledger

A backend service that holds a player's store of value (a Wallet) and records every change to it as a
permanent, immutable ledger. It must move money safely under concurrency and never apply the same request twice.

## Language

**Player**:
The account holder who owns a wallet.
_Avoid_: Customer, User, account

**Wallet**:
A single store of value belonging to one player, in exactly one currency.
_Avoid_: account, balance

**Balance**:
The current amount of value in a wallet, in minor units of the wallet's currency.
_Avoid_: total, amount

**Credit**:
A ledger change that increases a wallet's balance.
_Avoid_: deposit, top-up

**Debit**:
A ledger change that decreases a wallet's balance; rejected if it would make the balance negative.
_Avoid_: withdrawal, spend

**Ledger Entry**:
An immutable, permanent record of a single balance change: the player, the wallet, the amount,
the direction (credit or debit), the reason it happened, and the reference that makes it unique.
_Avoid_: transaction, record, log

**Reason**:
The explanation of *what* happened and *why* — the kind of change (a mission reward, a purchase, a refund)
and a free-form description.
_Avoid_: type, note, comment

**Refund**:
A new credit that reverses a prior debit, leaving that prior entry unchanged and adding a new immutable entry.
_Avoid_: rollback, reverse

**Minor Units**:
The unit in which every amount is stored and computed — a whole number of the smallest denomination
of the currency (e.g. cents). No fractions exist in the system.
_Avoid_: BigDecimal, double, units

**Idempotency Key**:
The client-supplied reference a request carries so the system can detect and ignore an identical repeated submission.
_Avoid_: request id, dedupe key

**Overdraft Guard**:
The rule that a debit is rejected if it would make a wallet's balance negative. A refund is a credit and is never subject to the guard.
_Avoid_: insufficient funds, NSF

**Duplicate Submission**:
A repeat of a request carrying an Idempotency Key the system has already applied; the original result is returned and nothing is applied twice.
_Avoid_: retry, re-entry
