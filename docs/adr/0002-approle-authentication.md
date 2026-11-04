# ADR-0002: Authenticate the application to Vault with AppRole

- Status: Accepted
- Date: 2026-10-27

## Context

The application must prove its identity to Vault before reading secrets. Whatever it uses
to log in is itself a secret, so the method decides how much damage a leak can do.

## Options considered

1. **Static Vault token** in an environment variable.
2. **AppRole** (`role_id` + `secret_id`), exchanged for a short-lived token.
3. **Kubernetes auth** (the pod's service-account JWT is the credential).

## Decision

AppRole for this service, because it works identically on a laptop, a VM and in a
container. The policy grants `read` on `secret/data/<app>` and `secret/data/application`
only. Tokens live 1 hour (renewed automatically by Spring Cloud Vault) with a 24-hour maximum,
after which the app logs in again.

When the service runs on Kubernetes, switch to Kubernetes auth: it needs no injected
secret at all and is the preferred option there.

## Consequences

- Positive: the root token is never used by the application; a leaked client token
  expires within the hour and can only read this app's secrets.
- Positive: `role_id` is not secret on its own; `secret_id` can be delivered per
  deployment by CI using response wrapping, so a person never sees it.
- Negative: `secret_id` is still a credential to protect and rotate. Local development
  pins fixed IDs for convenience; real environments must let Vault generate them.
