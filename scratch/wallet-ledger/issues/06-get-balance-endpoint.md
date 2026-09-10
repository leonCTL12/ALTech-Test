# 06: Get balance endpoint

**What to build:** `GET /players/{playerId}/wallet` that returns the Wallet's current Balance as a decimal string. An unknown player returns `404`. This endpoint becomes the standard way every later test asserts a resulting Balance through the API.

**Blocked by:** 05 Credit endpoint

**Status:** ready-for-agent

- [ ] After a credit, `GET` returns the correct Balance in decimal form
- [ ] A fresh player's Wallet returns `"0.00"`
- [ ] Unknown player returns `404`