# 06: Get balance endpoint

**What to build:** `GET /players/{playerId}/wallet` that returns the Wallet's current Balance as a decimal string. An unknown player returns `404`. This endpoint becomes the standard way every later test asserts a resulting Balance through the API.

**Blocked by:** 05 Credit endpoint

**Status:** done

- [x] After a credit, `GET` returns the correct Balance in decimal form
- [x] A fresh player's Wallet returns `"0.00"`
- [x] Unknown player returns `404`

## Comments

- Implemented `GET /players/{playerId}/wallet` on `dev.wallet.api.WalletController` (a `@GetMapping`
  with no path, matching the class-level `/players/{playerId}/wallet` mapping) delegating to
  `dev.wallet.service.WalletService#getBalance(long)`. It reads the Wallet via the non-locking
  `WalletRepository#findByPlayerId` (a read needs no `FOR UPDATE` lock) and returns the Balance as a
  decimal string via `MinorUnits.format(...)` in a `BalanceResponse(String balance)` record — the same
  `{ "balance": "..." }` shape the credit endpoint returns, so this endpoint is the standard way later
  tickets assert a resulting Balance through the API.
- Unknown player → `404 player_not_found` (same `orElseThrow` pattern as credit); non-positive path id
  → `400 invalid_path` via the shared `requirePlayerId`. No `@Transactional` needed: the read of one
  aggregate is atomic on its own.
- Tests: `src/test/java/dev/wallet/api/WalletBalanceControllerTest.java` (3 tests) at the spec's single
  seam (`@SpringBootTest` + MockMvc + real H2): fresh wallet `"0.00"`, balance after a credit
  (`"12.34"`), and `404` for an unknown player. Full suite 34 green.