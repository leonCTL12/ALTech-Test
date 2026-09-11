# 04: Create player endpoint

**What to build:** `POST /players` with an empty body. It creates a Player (server-generated id) and its Wallet at 0 Balance in one transaction, and returns `201` with the new `playerId`. This ticket also establishes the global exception handler that turns failures into the structured `{ code, message, field }` error body with the right HTTP status — the pattern every later endpoint reuses. A malformed or non-empty body returns `400`.

**Blocked by:** 03 JPA entities and repositories

**Status:** ready-for-agent

- [x] `POST /players` returns `201` with a `playerId`
- [x] Both a Player row and a Wallet row at 0 Balance exist afterwards
- [x] Malformed body returns `400` with the structured error body
- [x] Two calls create two independent players, each with its own wallet

## Comments

- Implemented `POST /players` (no `/api` prefix, per contract): `dev.wallet.api.PlayerController`
  delegates to `dev.wallet.service.PlayerService#createPlayer()`, which is `@Transactional` and
  saves the `Player` and its `Wallet` at balance 0 in one transaction, returning `201` with
  `{ "playerId": ... }`.
- Established the reusable error pattern: `dev.wallet.api.error.ApiException` (status/code/message/
  field), `ErrorResponse` record, and `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping
  `ApiException` → its status, malformed JSON → `400 malformed_body`, unexpected failures →
  `500 internal_error`. All later endpoints reuse this.
- The endpoint contract is "empty body": a valid-but-non-empty body returns `400 non_empty_body`,
  and a malformed JSON body returns `400 malformed_body`. Spring reads an `application/json` body as
  raw text for a `String` parameter, so the controller validates the JSON explicitly via `ObjectMapper`.
- Added `WalletRepository#findByPlayerId(Long)` (used by this ticket's tests and by later endpoints).
- Tests: `src/test/java/dev/wallet/api/PlayerControllerTest.java` (5 tests, green; full suite 27 green).