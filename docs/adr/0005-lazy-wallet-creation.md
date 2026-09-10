# 0005: Explicit player and wallet creation

A player and its wallet are created together by `POST /players` in a single transaction; the wallet
starts at a 0 balance and the player always has exactly one wallet. Any wallet operation for an unknown
player returns 404.

This reverses the earlier plan of lazy wallet creation on first credit (see git history). Why:

- The "insert wallet if absent" race on concurrent first-credits disappears entirely because the wallet
  exists before any money movement — one less edge case to prove safe.
- Player lifecycle (who exists) and wallet lifecycle (store of value) are cleanly separated.
- The repo stays self-contained and demoable: a reviewer can create a player through the API and exercise
  every endpoint without touching the database.

Known trade-off: in a real deployment a player would already exist in an external identity system, and the
wallet would be created on first top-up. The explicit endpoint is a deliberate simplification for a
self-contained take-home.