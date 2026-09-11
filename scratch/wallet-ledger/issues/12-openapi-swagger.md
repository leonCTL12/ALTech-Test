# 12: OpenAPI/Swagger documentation

**What to build:** springdoc wired in so the running application serves an OpenAPI description and the Swagger UI listing every endpoint — create player, credit, debit, refund, get balance, transaction history — with their request bodies, parameters, and error statuses.

**Blocked by:** 08 Refund endpoint, 09 Transaction history pagination, 10 Idempotency for repeated and concurrent identical requests

**Status:** done

- [x] Application boots and serves the OpenAPI JSON
- [x] Swagger UI is reachable at `/swagger-ui`
- [x] Every endpoint appears in the description with its body/parameters and documented error responses

## Comments

- Wired in `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9` (the Boot 3.x-compatible
  springdoc starter). The running application now serves the OpenAPI description at `/v3/api-docs` and the
  Swagger UI at `/swagger-ui/index.html`.
- Documented every endpoint on `PlayerController` and `WalletController` with `@Operation` and `@ApiResponse`
  annotations: create player, get balance, transaction history, credit, debit, and refund. Each endpoint lists
  its success schema (the existing response records) and its error statuses with the specific error codes the
  service can return (e.g. `insufficient_balance`, `already_refunded`, `debit_not_found`, `player_not_found`,
  `missing_field`, `invalid_amount`). The `after`/`limit` query parameters on history are annotated with `@Parameter`.
- The review caught a pre-existing gap that the new docs would have codified wrongly: a non-numeric `playerId`
  (e.g. `/players/abc/wallet`) previously fell through to the generic handler and returned `500`. Added a
  `MethodArgumentTypeMismatchException` handler in `GlobalExceptionHandler` so it now returns `400 invalid_path`,
  matching what the OpenAPI documents.
- Tests: `src/test/java/dev/wallet/api/OpenApiDocumentationTest.java` (3 tests) verifies the OpenAPI JSON is
  served, the Swagger UI is reachable, every endpoint path is present, and each endpoint documents its success
  and error response schemas; `WalletBalanceControllerTest` gains a non-numeric-path `400` test. Full suite now
  65 green.