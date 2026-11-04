# ADR-0003: Separate schema-owner and application database roles; Flyway owns the schema

- Status: Accepted
- Date: 2026-10-27

## Context

If the running application connects as the role that owns the tables, any SQL injection or
bug can `DROP`, `ALTER` or `TRUNCATE`. Letting Hibernate manage the schema (`ddl-auto=update`)
makes production schema changes implicit, unreviewed and irreversible.

## Options considered

1. One role for everything, `ddl-auto=update`.
2. One role for everything, Flyway migrations.
3. **Two roles**: `demo_owner` owns schema `demo` and runs Flyway; `demo_app` has only
   `SELECT/INSERT/UPDATE/DELETE` through default privileges. Hibernate only validates.

## Decision

Option 3. Both credentials live in Vault (`spring.flyway.*` and `spring.datasource.*`).
Guard rails are set on the database role itself (`statement_timeout`, `lock_timeout`,
`idle_in_transaction_session_timeout`) so they apply to every client, not just this app.
Invariants that matter (unique, lower-cased email, non-blank name) are database
constraints, not only Java validation.

## Consequences

- Positive: the runtime role cannot change the schema; migrations are versioned and reviewed;
  `ddl-auto=validate` stops startup if code and schema drift apart.
- Positive: in production, migrations can run as a separate job with the owner credential,
  so running pods never hold DDL rights (`DB_MIGRATE_ON_STARTUP=false` in the prod profile).
- Negative: one more role to provision; new objects need default privileges (already set up
  in `docker/postgres/init/01-roles.sh`).
