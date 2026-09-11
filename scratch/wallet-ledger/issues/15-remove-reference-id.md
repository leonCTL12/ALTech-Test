# 15: Remove `referenceId` from Reason

**What to build:** drop the `referenceId` field from the domain model (`Reason`), all DTOs (`ReasonInput`, `ReasonResponse`), the API boundary (controller acceptance + parsing), the schema migration, and the tests/docs that reference it. After removal, every ledger entry still stores its reason fully via `reasonKind` + `description` — no data is lost.

**Blocked by:** none

**Status:** ready-for-agent

- [ ] Schema: remove `reference_id` column from `ledger_entry` table
- [ ] Domain: remove `referenceId` from `Reason` value object and constructor
- [ ] API: remove `referenceId` from `ReasonInput` and `ReasonResponse` records
- [ ] Controller: remove `referenceId` from the parse helper in `WalletController`
- [ ] OpenAPI annotations: update any `@Schema` / `@Parameter` that referenced `referenceId`
- [ ] Tests: remove assertions against `reason.referenceId` in history response; verify endpoints compile
- [ ] Docs: update `LedgerHistoryResponse` example in `OpenApiDocumentationTest` and any other references

## What `referenceId` was supposed to do — and why it is cut

The original design allowed attaching an optional external business reference to each ledger entry
(e.g. a mission id or purchase order id). In practice nothing sets it, nothing reads it, and no endpoint
needs it. The `description` field already satisfies the spec's requirement for "a clear, permanent record
of what happened and why". Removing it eliminates dead code and simplifies the Reason model without
regressing any capability.

## Implementation notes

- Edit `V1__create_schema.sql` directly instead of writing a Flyway migration V2. Nothing has been deployed
  yet; a fresh migration file is cleaner than an empty-or-drop migration.
- Update `LedgerEntryRepository` if there are any queries referencing `reference_id`.
- Verify `FlywaySchemaTest`, `JpaMappingTest`, and `MinorUnitsTest` still pass (mapping test may exercise
  the entity columns).
