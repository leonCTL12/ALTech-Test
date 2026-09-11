# 14: README with the five required sections

**What to build:** the reviewer-facing README with the five required sections: how to run the project and tests; design decisions (single-entry ledger with materialized Balance, Money as Minor Units, USD-only, reason embedded in LedgerEntry rather than its own table — see ADR-0006); how concurrency and Idempotency are handled (Overdraft Guard conditional update, UNIQUE constraint on the Idempotency Key, atomic transaction); the testing approach, especially the concurrent debit case; and assumptions and limitations (players created in-service rather than external identity, no multi-currency/FX, no auth).

**Blocked by:** 13 PostgreSQL profile (optional)

**Status:** ready-for-agent

## Comments

- Superseded by direct authoring: the README (`/README.md`) was written in a grilling session with the
  author rather than through this ticket. The checklist below is satisfied; the README additionally gained
  a curated design-decisions list, an expanded concurrency section (database-as-guard paragraph), a
  four-item future-work list, and a "How this was built" agentic-workflow section. `CONTEXT.md` gained the
  two idempotency senses as canonical terms.

- [ ] How to run: prerequisites, database (H2 default, PostgreSQL profile), and the test command
- [ ] Design decisions section explains the ledger approach and trade-offs
- [ ] Concurrency & Idempotency section explains both mechanisms **and the deliberate response-idempotency deviation** (see the contract below)
- [ ] Testing approach section explains the HTTP-seam strategy and the concurrent debit case
- [ ] Assumptions & limitations section records the known gaps

## README must document a deliberate idempotency contract (decided with the author)

The **Concurrency & Idempotency** section must state that we deliberately deviate from textbook *response*
idempotency, why, and what we kept instead. The author wants this as an interview talking point, so give it
the two-senses framing and a concrete example.

### Two senses of idempotency (lead with this)

1. **Effect idempotency** — replaying a request has no extra effect (never applied twice). We keep this
   fully, guaranteed by the DB UNIQUE constraint on the Idempotency Key; a concurrent duplicate is never a
   conflict. This is the part that protects money.
2. **Response idempotency** — a replay returns the *same response body* as the first call (Stripe-style
   behaviour). We deliberately do **not** hold this: a replay returns `200` with the wallet's *true current
   balance*, which may differ from the original response if other operations ran in between.

In one line: **we hold effect idempotency and relax response idempotency, on purpose.**

### Why (use case over textbook)

The POST echo's practical job is "where does the wallet stand now"; `GET /wallet` is the authoritative
read, and the ledger records what each request did. Honouring response idempotency would mean echoing a
stale historical balance — which makes a UI show an outdated number — a worse lie than a truthful current
balance. The assignment only requires "the same request must not be applied more than once", which is the
part we guarantee unconditionally.

### The short example (include in the README)

1. `debit 4.00` `requestId=R1` → balance 10.00 → 6.00, response `{"balance":"6.00"}` (response lost in transit)
2. `debit 4.00` `requestId=R2` → balance 6.00 → 2.00 (a second, different purchase)
3. Client retries R1 → we return `200 {"balance":"2.00"}` — the true current balance

A textbook response-idempotent service would replay `{"balance":"6.00"}`. We return the real `2.00`. R1 was
still applied exactly once (effect idempotency holds — exactly one Ledger Entry for R1); the response just
answers "where does the wallet stand", and the ledger answers "what did R1 do".

### Interview talking points

- Name the two senses and say which you kept and which you relaxed — most candidates can only repeat the
  textbook definition, and splitting it shows you understand the subject, not just the definition.
- Justify by use case: a stale echo is more dangerous for a UI caller than a truthful one; the PDF only
  requires "same request not applied more than once", which we satisfy fully.
- Be upfront about the deviation — don't claim full response idempotency; frame it as a deliberate trade
  and point out the UNIQUE constraint is what makes the trade safe.
- If pressed on "same request, same result": the *outcome* is the same (applied once, `200`, never a
  conflict); the authoritative result is `GET /wallet` and the ledger.