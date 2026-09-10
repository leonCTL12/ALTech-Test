# 12: OpenAPI/Swagger documentation

**What to build:** springdoc wired in so the running application serves an OpenAPI description and the Swagger UI listing every endpoint — create player, credit, debit, refund, get balance, transaction history — with their request bodies, parameters, and error statuses.

**Blocked by:** 08 Refund endpoint, 09 Transaction history pagination, 10 Idempotency for repeated and concurrent identical requests

**Status:** ready-for-agent

- [ ] Application boots and serves the OpenAPI JSON
- [ ] Swagger UI is reachable at `/swagger-ui`
- [ ] Every endpoint appears in the description with its body/parameters and documented error responses