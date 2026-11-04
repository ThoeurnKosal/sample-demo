# ADR-0004: Package by feature with a thin web layer, not full hexagonal architecture

- Status: Accepted
- Date: 2026-10-27

## Context

The service has one aggregate (`Customer`) and one adapter on each side (HTTP in, JPA out).
The structure must keep the HTTP contract and persistence from leaking into each other
without adding ceremony that a service this size does not need.

## Options considered

1. **Package by layer** (`controller/`, `service/`, `repository/`).
2. **Package by feature** (`customer/`, `customer/web/`) with a service layer.
3. **Hexagonal / ports and adapters** with domain-owned repository interfaces and mappers.

## Decision

Option 2. Everything about customers lives in `customer`; HTTP-specific types live in
`customer.web`. Request/response records never cross into the service (it takes
`CustomerDetails`), and the JPA entity is never serialised.

## Consequences

- Positive: a feature can be read, changed or extracted as one folder; the API contract can
  evolve without touching persistence.
- Negative: the service depends on Spring Data directly. If a second persistence adapter or
  heavy domain logic appears, introduce a port interface then (option 3); doing it now would
  add mapping code with no current payoff.
