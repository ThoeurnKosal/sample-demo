# ADR-0001: Keep database credentials in HashiCorp Vault (KV v2)

- Status: Accepted
- Date: 2026-10-27

## Context

The service needs PostgreSQL credentials. Committing them to `application.yml`, a `.env`
file in the repository or a CI variable that every pipeline can read spreads the secret
to every developer laptop, build log and image layer. Rotation then means a code change
and a redeploy everywhere the value was copied. The organisation already intends to
centralise secrets with an audit trail.

## Options considered

1. **Environment variables / Kubernetes Secrets** fed by the deployment pipeline.
2. **Cloud secret manager** (AWS Secrets Manager, GCP Secret Manager, Azure Key Vault).
3. **HashiCorp Vault, KV v2 static secrets**, read at startup through Spring Cloud Vault.
4. **HashiCorp Vault, database secrets engine** (dynamic, per-instance credentials).

## Decision

Option 3. Spring Cloud Vault imports `secret/<app>` (and `secret/<app>/<profile>`) as a
property source, so the keys are plain Spring property names
(`spring.datasource.password`, ...) and the application code has no Vault dependency at all.

Option 4 is the target end state; it is deferred, not rejected (see Consequences).

## Consequences

- Positive: no credentials in Git, images or Spring config files; every read is
  authenticated, authorised by policy and audit-logged; KV v2 keeps versions, so a bad
  rotation can be rolled back with `vault kv rollback`.
- Positive: vendor-neutral across clouds and on-premises; the same workflow works on a laptop.
- Negative: Vault becomes a startup dependency. With `fail-fast: true` the app refuses to
  start without it. That is deliberate: starting with missing credentials only moves the
  failure to the first request.
- Negative: static secrets still need a rotation procedure (change in PostgreSQL, write to
  Vault, rolling restart). Dynamic secrets (option 4) remove that, at the cost of handling
  credential expiry in the connection pool.
- Risk: Vault in dev mode (used locally) is in-memory and unsealed with a known root token.
  It must never be used outside a developer machine; production needs HA storage, TLS,
  auto-unseal and an audit device.
