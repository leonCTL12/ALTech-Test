# 04: Create player endpoint

**What to build:** `POST /players` with an empty body. It creates a Player (server-generated id) and its Wallet at 0 Balance in one transaction, and returns `201` with the new `playerId`. This ticket also establishes the global exception handler that turns failures into the structured `{ code, message, field }` error body with the right HTTP status — the pattern every later endpoint reuses. A malformed or non-empty body returns `400`.

**Blocked by:** 03 JPA entities and repositories

**Status:** ready-for-agent

- [ ] `POST /players` returns `201` with a `playerId`
- [ ] Both a Player row and a Wallet row at 0 Balance exist afterwards
- [ ] Malformed body returns `400` with the structured error body
- [ ] Two calls create two independent players, each with its own wallet