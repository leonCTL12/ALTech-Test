# 13: PostgreSQL profile (optional)

**What to build:** a `postgres` Spring profile pointing at a local PostgreSQL instance plus an optional Docker Compose file, running the same Flyway SQL. The H2 tests must also pass against PostgreSQL, proving the schema is portable.

**Blocked by:** 12 OpenAPI/Swagger documentation

**Status:** done

- [x] Service boots against PostgreSQL with the `postgres` profile and applies the same Flyway migration
- [x] The test suite passes against PostgreSQL without code changes
- [x] Docker Compose (if included) brings up the database with documented commands

## Comments

- Added the `org.postgresql:postgresql` JDBC driver (runtime scope) and a `postgres` Spring profile
  (`src/main/resources/application-postgres.properties`) pointing at `jdbc:postgresql://localhost:5432/wallet`
  (user/password `wallet`), with `ddl-auto=none` so Flyway owns the schema exactly as on H2.
- Added `docker-compose.yml` (PostgreSQL 16) that creates the `wallet` database, with a healthcheck and a
  named data volume. Commands are documented in the file header:
  `docker compose up -d`, then
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres` (run) or
  `SPRING_PROFILES_ACTIVE=postgres ./mvnw test` (tests). `-Dspring.profiles.active=postgres` also works.
- **Portability fix (an acknowledged deviation from the criterion's literal wording):** criterion 2 said
  "passes against PostgreSQL without code changes"; the one H2-specific test — `FlywaySchemaTest` — matched
  `INFORMATION_SCHEMA` by uppercase table name (H2 stores unquoted names uppercase, PostgreSQL lowercase) and
  would not pass on PostgreSQL as written. The two queries are now case-insensitive with
  `UPPER(TABLE_NAME) = UPPER(?)`. That single test edit is the only change needed; the migration and all
  production code are database-agnostic and untouched.
- Verified: full suite (66 tests) green against PostgreSQL and again against the default H2; the service
  boots on the `postgres` profile and served health + a credit round-trip during a smoke test.
- Decision: tests and the running app share the single `wallet` database (no separate `wallet_test` DB).
  Safe because every test creates its own player and uses random idempotency keys, so runs never collide.
  Simplicity wins for a take-home. Known limitations of sharing: state accumulates in the DB across test
  runs, and running the suite and the app at the same time makes both Flyway-apply against the same database
  (harmless here — Flyway is already-applied and re-runs are skipped). A separate test DB is the documented
  future option.