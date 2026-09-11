# 13: PostgreSQL profile (optional)

**What to build:** a `postgres` Spring profile pointing at a local PostgreSQL instance plus an optional Docker Compose file, running the same Flyway SQL. The H2 tests must also pass against PostgreSQL, proving the schema is portable.

**Blocked by:** 12 OpenAPI/Swagger documentation

**Status:** ready-for-agent

- [ ] Service boots against PostgreSQL with the `postgres` profile and applies the same Flyway migration
- [ ] The test suite passes against PostgreSQL without code changes
- [ ] Docker Compose (if included) brings up the database with documented commands