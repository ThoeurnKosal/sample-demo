# sample-demo

A sample **Spring Boot 4.1 / Java 21 / Gradle** REST service for **customers** and their
**accounts** (wallets), built as ports and adapters (hexagonal), one module per business area.

- Every API call needs an **OAuth2 access token (JWT)** from **Keycloak**.
- Data lives in **PostgreSQL**; database and Redis credentials come from **HashiCorp Vault**.
  Nothing secret is written in `application*.yml`.
- Users and posts come from an external directory (**JSONPlaceholder**) and are cached in
  **Redis**.
- Every request/response is logged once, with credentials and personal data masked.

```
                 1. POST /realms/demo/.../token     ┌──────────────────────┐
  ┌──────────┐ ───────────────────────────────────▶ │ Keycloak :8180       │
  │   API    │ ◀─────────────────────────────────── │ realm demo           │
  │  client  │        access_token (JWT, 5 min)     └──────────────────────┘
  │          │                                                 ▲ JWKS
  │          │ 2. Authorization: Bearer <JWT>       ┌──────────┴───────────┐
  └──────────┘ ───────────────────────────────────▶ │ sample-demo          │
                                                    │ :8080 API, :8081 ops │
                                                    └─┬───────┬───────┬──┬─┘
                                  credentials (AppRole)│   JDBC│  cache│  │ HTTPS, cache miss only
                                       ┌──────────────▼┐ ┌────▼───┐ ┌─▼──────┐ ┌▼───────────────┐
                                       │ Vault :8200   │ │Postgres│ │ Redis  │ │ JSONPlaceholder│
                                       │ secret/demo   │ │ :5432  │ │ :6379  │ │ (external)     │
                                       └───────────────┘ └────────┘ └────────┘ └────────────────┘
```

What is in Vault (`secret/demo`, KV v2):

```
spring.datasource.username / password   demo_app    reads and writes data only (DML)
spring.flyway.user / password           demo_owner  owns schema "demo", runs migrations (DDL)
spring.data.redis.username / password   demo        Redis ACL user, keys demo:* only
```

More detail: [architecture, diagrams, API contract](docs/architecture.md) and the
[architecture decision records](docs/adr/).

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Docker Desktop (or Docker Engine) | with Compose v2 | `docker compose version` |
| Postman (optional) | any | to call the API with a token |

Use the Gradle wrapper (`./gradlew`); no Gradle installation needed. Ports `5432`, `8200`,
`8180`, `6379`, `8080` and `8081` must be free (the first four can be changed in `.env`).

Commands are for **Windows PowerShell**. On macOS / Linux use `cp` instead of `Copy-Item`
and `export VAR=value` instead of `$env:VAR = "value"`.

---

## Step 1: Create your local `.env`

```powershell
Copy-Item .env.example .env
```

`.env` holds **local, throwaway** values for Docker Compose (database roles, Vault seed,
Keycloak clients, Redis user) and for `./gradlew bootRun`. It is in `.gitignore`; real
environments never use it.

Already have an older `.env`? Copy the **Keycloak** and **Redis** blocks from `.env.example`,
otherwise `docker compose up` stops with `KEYCLOAK_IMAGE_TAG missing` or `REDIS_IMAGE_TAG missing`.

## Step 2: Start the infrastructure

```powershell
docker compose up -d
docker compose ps
```

Expected: `postgres`, `vault`, `keycloak` and `redis` are `healthy` (Keycloak needs ~30 s on first
start) and `vault-init` has `exited (0)`.

```powershell
docker compose logs vault-init    # last line: Vault seeded: secret/demo, policy demo-read, approle demo.
```

What just happened:

1. **PostgreSQL** ran `docker/postgres/init/01-roles.sh` (empty volume only): schema `demo`,
   role `demo_owner` (DDL) and role `demo_app` (DML only, with statement/lock timeouts).
2. **Vault** started in dev mode; `vault-init` wrote the six credentials above, the read-only
   policy `demo-read` and the AppRole `demo` the app logs in with.
3. **Keycloak** imported realm `demo` from `docker/keycloak/realm-demo.json`.
4. **Redis** started as a cache only (no persistence, 128 MB, LRU eviction). The `default` user
   is disabled; the app uses ACL user `demo`, limited to keys `demo:*`.

| Keycloak client | Scopes in the token | Use it to |
|---|---|---|
| `demo-service` | `customers:* accounts:*` (+ `ops:read` on request) | call every endpoint |
| `demo-readonly` | `customers:read accounts:read` | check that writes return 403 |

## Step 3: Look around (optional)

```powershell
# Vault (UI: http://127.0.0.1:8200, token local-root-token)
docker compose exec -e VAULT_TOKEN=local-root-token vault vault kv get secret/demo

# Keycloak admin console: http://127.0.0.1:8180 (KEYCLOAK_ADMIN_USER / KEYCLOAK_ADMIN_PASSWORD), realm "demo"

# Redis, as the app user (KEYS is denied by design: use SCAN)
docker compose exec redis redis-cli --no-auth-warning --user demo --pass local-redis-pw --scan --pattern "demo:*"
```

## Step 4: Run the tests

```powershell
./gradlew test
```

Tests never contact Vault, Keycloak or Redis: `src/test/resources/config/application.yml`
disables Vault and sets `spring.cache.type: none`; security tests use a mocked `JwtDecoder`
(`@MockitoBean`) and `SecurityMockMvcRequestPostProcessors.jwt()`.

## Step 5: Run the application

```powershell
./gradlew bootRun
```

`bootRun` activates the `local` profile and passes only an allowlist of variables from `.env`
(AppRole ids, `DB_NAME`, `KEYCLOAK_HOST_PORT`; see `build.gradle.kts`). Database, Redis and
Keycloak passwords in `.env` never reach the app: it reads them from Vault.

Check the startup log:

- `The following 1 profile is active: "local"`
- Flyway: `Successfully applied ... migrations to schema "demo"` (V1, V2, V3), then `Started DemoApplication`
- **No** `Using generated security password` line (it would mean the JWT chain is not active)

## Step 6: Get an access token

Tokens live **5 minutes**; get a new one on a 401.

```powershell
./scripts/get-token.ps1                               # prints iss, aud, scope, expiry and the token
$token = ./scripts/get-token.ps1 -Quiet               # demo-service: read + write
$ro    = ./scripts/get-token.ps1 -Client demo-readonly -Quiet
$ops   = ./scripts/get-token.ps1 -Scope ops:read -Quiet
$auth  = @{ Authorization = "Bearer $token" }
```

If PowerShell refuses scripts: `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`.

**curl / Postman** (Postman: Import → Raw text):

```bash
curl --location 'http://127.0.0.1:8180/realms/demo/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=demo-service' \
--data-urlencode 'client_secret=local-demo-service-secret'
```

In Postman, save it in a collection with this **Post-response** script, then use
`Bearer {{accessToken}}` in the other requests:

```javascript
pm.collectionVariables.set("accessToken", pm.response.json().access_token);
```

Details, claims and errors: [Token API](docs/architecture.md#token-api-keycloak).

## Step 7: Call the API

### Customers

```powershell
# Register (201 + Location; 409 DUPLICATE_CUSTOMER if the email exists)
$body = @{ fullName = "Dara Sok"; email = "dara@example.com"; phone = "012 345 678"; city = "Phnom Penh" } | ConvertTo-Json
$created = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/customers `
  -Headers $auth -ContentType "application/json" -Body $body

# Read, list (newest first; size 20-100)
Invoke-RestMethod "http://localhost:8080/api/v1/customers/$($created.id)" -Headers $auth
Invoke-RestMethod "http://localhost:8080/api/v1/customers?page=0&size=20" -Headers $auth

# Import from the directory (201 created, 200 already imported, 404 unknown directory user)
$import = @{ externalUserId = 1 } | ConvertTo-Json
$imported = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/customers/imports `
  -Headers $auth -ContentType "application/json" -Body $import

# Posts of an imported customer (from the directory, cached 5 min)
Invoke-RestMethod "http://localhost:8080/api/v1/customers/$($imported.id)/posts" -Headers $auth
```

### Accounts

One **ACTIVE** account per customer and currency (`USD`, `KHR`); at most two decimals; the
balance never goes below zero; only an empty account can be closed.

```powershell
$open = @{ currency = "USD" } | ConvertTo-Json
$account = Invoke-RestMethod -Method Post "http://localhost:8080/api/v1/customers/$($created.id)/accounts" `
  -Headers $auth -ContentType "application/json" -Body $open

$amount = @{ amount = 25.50 } | ConvertTo-Json
Invoke-RestMethod -Method Post "http://localhost:8080/api/v1/accounts/$($account.id)/deposits" `
  -Headers $auth -ContentType "application/json" -Body $amount

$amount = @{ amount = 25.50 } | ConvertTo-Json
Invoke-RestMethod -Method Post "http://localhost:8080/api/v1/accounts/$($account.id)/withdrawals" `
  -Headers $auth -ContentType "application/json" -Body $amount

Invoke-RestMethod "http://localhost:8080/api/v1/customers/$($created.id)/accounts" -Headers $auth
Invoke-RestMethod -Method Post "http://localhost:8080/api/v1/accounts/$($account.id)/close" -Headers $auth
```

Deposits and withdrawals are **not idempotent** yet: do not blindly retry them after a timeout.

### Expected results

| Call | Expected |
|---|---|
| Any call without `Authorization` | **401** `UNAUTHORIZED` |
| A write with `$ro` (`demo-readonly`) | **403** `ACCESS_DENIED` |
| Same email registered twice | **409** `DUPLICATE_CUSTOMER` |
| Second active USD account for one customer | **409** `ACCOUNT_ALREADY_EXISTS` |
| Withdraw more than the balance | **422** `INSUFFICIENT_FUNDS` |
| Amount `0`, negative or `1.234` | **400** `VALIDATION_FAILED` |
| Close an account with money | **422** `ACCOUNT_BALANCE_NOT_ZERO` |
| `Accept-Language: km` on any error | Khmer `title`/`detail`, same `errorCode` |

Every error is `application/problem+json`:

```json
{
  "type": "https://problems.sample-demo.example/unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authentication is required. Send a valid bearer token.",
  "instance": "/api/v1/customers",
  "errorCode": "UNAUTHORIZED",
  "traceId": "290475eee464cd26dd5d3b6bd9a10745",
  "timestamp": "2026-11-03T06:40:42.503Z"
}
```

### Cache (Redis)

```powershell
# Same call twice: the second one comes from Redis (compare durations in the HTTP log)
Invoke-RestMethod "http://localhost:8080/api/v1/customers/$($imported.id)/posts" -Headers $auth
Invoke-RestMethod "http://localhost:8080/api/v1/customers/$($imported.id)/posts" -Headers $auth
```

In `redis-cli` (user `demo`):

```
SCAN 0 MATCH demo:* COUNT 100
GET "demo:v1:directory-posts::1"
TTL "demo:v1:directory-posts::1"
DEL "demo:v1:directory-posts::1"
```

Kill switch (also in production): `$env:SPRING_CACHE_TYPE = "none"`, then restart the app.

### Without Keycloak

Opt-in profile `noauth` turns authentication off (laptops only); `@PreAuthorize` still runs:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local,noauth"; ./gradlew bootRun
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

## Step 8: Logs, health and metrics

### Logs

| Output | Content |
|---|---|
| Console | ECS JSON, one line per event, `traceId` on every line |
| `logs/http.log` | HTTP traffic only (`HttpLoggingFilter`), one block per request/response |
| `logs/demo.log` | Everything else |
| `logs/archive/*.log.gz` | Rolled every hour, kept 72 h |

With `app.http-logging.pretty: true` (local) a request looks like:

```
┌──────────────────────────────────────────────────────────────
│ REQUEST   POST http://localhost:8080/api/v1/customers
│ ── headers
│     Authorization  : Bearer ****
│     Content-Type   : application/json
│ ── body
│     {
│       "fullName": "Stane Chu",
│       "email": "s***@example.com",
│       "phone": "*** *** 670"
│     }
├──────────────────────────────────────────────────────────────
│ RESPONSE  201 Created  (35 ms)
│ ...
└──────────────────────────────────────────────────────────────
```

```powershell
Get-Content logs\http.log -Wait -Tail 20                    # follow HTTP traffic
Select-String -Path logs\*.log -Pattern "<traceId>"         # everything about one request
```

Human-readable console instead of JSON on your laptop, in `application-local.yml`:
`logging.structured.format.console: ""`. If box characters look broken: `chcp 65001`.

### Health and metrics (port 8081)

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health/readiness   # includes PostgreSQL only
Invoke-RestMethod http://localhost:8081/actuator/health/liveness
(Invoke-WebRequest http://localhost:8081/actuator/prometheus).Content -split "`n" |
  Select-String 'api_errors|cache_gets|cache_errors|hikaricp_connections_pending'
```

Alert on: `api_errors_total` rate by `errorCode`, `cache_errors_total` > 0,
`hikaricp_connections_pending` > 0, circuit breaker state `open`.

## Step 9: Rotate a database password

```powershell
docker compose exec postgres psql -U postgres -d mydb -c "ALTER ROLE demo_app PASSWORD 'rotated-app-pw'"
docker compose exec -e VAULT_TOKEN=local-root-token vault vault kv patch secret/demo spring.datasource.password=rotated-app-pw
# restart the app; also update DB_APP_PASSWORD in .env so vault-init does not restore the old value
```

Undo in Vault: `vault kv rollback -version=1 secret/demo`.

## Step 10: Stop and clean up

```powershell
docker compose down        # stop, keep the database volume
docker compose down -v     # stop and delete all data (Flyway re-applies V1..Vn on next start)
```

Vault, Keycloak and Redis keep nothing on disk. If only `vault` restarts:
`docker compose up vault-init`, then restart the app. After editing `realm-demo.json`:
`docker compose up -d --force-recreate keycloak` and get new tokens.

---

## Project structure

```
├── compose.yaml                         PostgreSQL, Vault, vault-init, Keycloak, Redis
├── .env.example                         local values (copy to .env)
├── build.gradle.kts                     bootRun reads an allowlist of variables from .env
├── docker/
│   ├── postgres/init/01-roles.sh        schema, owner role, app role, timeouts
│   ├── vault/init.sh                    secrets (DB + Redis), policy, AppRole, self-check
│   └── keycloak/realm-demo.json         realm demo: scopes, clients, audience mapper
├── scripts/get-token.ps1                token from the local Keycloak, prints its claims
├── docs/
│   ├── architecture.md                  C4 views, security, data model, API contract, failure modes
│   └── adr/                             0001-0005 decision records
├── logs/                                local log files (demo.log, http.log, archive/)
└── src/main/
    ├── resources/
    │   ├── application.yml              shared config (Vault, DB, Redis, cache, HTTP clients, resilience)
    │   ├── application-local.yml        laptop overrides
    │   ├── logback-spring.xml           log files, hourly roll-over
    │   ├── messages*.properties         error texts (English default, Khmer)
    │   └── db/migration/                V1 customer, V2 customer.updated_at, V3 account
    └── java/com/mysample/demo/
        ├── customer/
        │   ├── api/                     CustomerController, CustomerApiMapper, dto/
        │   ├── application/             CustomerService, ImportResult
        │   ├── domain/                  Customer, ExternalUser, CustomerPost, exception/, port/
        │   └── infrastructure/
        │       ├── persistence/         JPA entity, repository, adapter
        │       └── client/jsonplaceholder/  HTTP interface, JsonPlaceholderAdapter, DirectoryCaches, dto/
        ├── account/
        │   ├── api/                     AccountController, AccountApiMapper, dto/
        │   ├── application/             AccountService
        │   ├── domain/                  Account (money rules), exception/, port/
        │   └── infrastructure/
        │       ├── persistence/         JPA entity, repository, adapter
        │       └── customer/            CustomerModuleAdapter (asks customer.application)
        └── shared/
            ├── cache/                   CacheConfig, AppCacheProperties, MeteredCacheErrorHandler
            ├── config/                  SecurityConfig, HTTP client config
            ├── error/                   ErrorCode, exception/, security/ (401/403)
            ├── handler/                 GlobalExceptionHandler, ProblemDetailFactory
            ├── mapping/                 MapStruct @MapperConfig policies
            ├── model/                   shared types
            ├── observability/           HttpLoggingFilter, SensitiveDataMasker, DownstreamLoggingInterceptor
            └── pagging/                 paging helpers
```

Dependencies point inward (`api` / `infrastructure` → `application` → `domain`); the domain
never imports Spring, JPA, HTTP or Redis classes; modules talk only through application APIs.

## Configuration reference

| Variable / property | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` (bootRun) | `local`, `local,noauth`, `uat`, `prod` |
| `VAULT_ADDR`, `VAULT_APP_ROLE_ID` / `VAULT_APP_SECRET_ID` | `http://localhost:8200`, from `.env` | Vault login |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `mydb` | Database location |
| `DB_POOL_SIZE`, `DB_MIGRATE_ON_STARTUP` | `10`, `true` | Pool size; run Flyway at startup |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis location (credentials from Vault) |
| `SPRING_CACHE_TYPE` | `redis` | `none` turns caching off |
| `app.cache.ttl.*`, `app.cache.key-prefix` | 30 min users, 5 min posts, `demo:v1:` | Cache TTLs and key version |
| `JSONPLACEHOLDER_BASE_URL` | `https://jsonplaceholder.typicode.com` | Directory URL |
| `KEYCLOAK_HOST_PORT` | `8180` | Local Keycloak; part of the issuer URI |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` / `audiences` | local Keycloak / `demo-api` | Token validation |
| `app.http-logging.*` | compact, no headers, no bodies | HTTP log detail (`pretty`, `include-headers`, `include-body`, masked fields) |
| `MANAGEMENT_PORT` | `8081` | Actuator port |

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `KEYCLOAK_IMAGE_TAG` / `REDIS_IMAGE_TAG missing in .env` | Old `.env`: copy the block from `.env.example`. |
| `vault-init`: `REDIS_APP_USER: parameter not set` | `compose.yaml` does not pass `REDIS_APP_USER` / `REDIS_APP_PASSWORD` to `vault-init`. Add them, `docker compose up vault-init`, restart the app. |
| `port is already allocated` (5432, 8200, 8180, 6379) | Change `*_HOST_PORT` in `.env`, then `docker compose down` and `up -d`. |
| `permission denied` / `invalid role or secret ID` from Vault | Vault restarted (dev mode). `docker compose up vault-init`, restart the app. |
| `password authentication failed for user "demo_app"` | Volume created with other passwords: fix `.env`, or `docker compose down -v`. |
| `Migration description mismatch` / `checksum mismatch` | A released migration was renamed or edited. Local: `docker compose down -v`. Shared environments: restore the file, add a new `V<n+1>`. |
| `Schema-validation: missing table [account]` | V3 did not run (or was empty). Check `flyway_schema_history`. |
| `required a bean of type 'JwtDecoder'` | No `issuer-uri` for the active profile. |
| `Using generated security password`, empty 401, `JSESSIONID` cookie | None of our filter chains is active: check `@Profile` in `SecurityConfig`. |
| 401 with a token | Read `WWW-Authenticate`: expired (5 min), `iss` ≠ `issuer-uri`, or `aud` without `demo-api`. |
| 403 with a token / on account endpoints | Missing scope. After realm changes: `docker compose up -d --force-recreate keycloak` and a new token. |
| Token request `invalid_client` / `Missing form parameter: grant_type` | Wrong secret / body sent as JSON instead of form data. |
| Nothing in Redis | Call `/posts` or `/imports` for an **imported** customer; check `cache_errors_total` and `Cache ... failed` warnings (often missing Redis credentials in Vault). |
| `NOPERM ... 'keys' command` in a Redis tool | By design: use `SCAN 0 MATCH demo:*`. |
| 400 `VALIDATION_FAILED` on `?size=10` | `size` is 20-100 today (see Known gaps in the architecture). |
| 400 with `fullName` / `email` null | JSON keys do not match the DTO; unknown keys are ignored silently. |
| `Accept-Language: km` ignored | File must be `messages_km.properties` (not `_kh`), UTF-8, key `error.<slug>`. |

## Before production

- **Identity:** a realm per environment behind TLS; client secrets in Vault or the platform
  secret store; one client per caller with the fewest scopes; delete the `noauth` profile.
- **Money:** add the `account_transaction` ledger with `Idempotency-Key` before real money moves.
- **Vault:** HA (Raft), TLS, auto-unseal, audit device, root token revoked; Kubernetes auth on
  Kubernetes, otherwise AppRole with a CI-delivered, response-wrapped `secret_id`.
- **Database:** TLS (`sslmode=verify-full`); migrations as a separate job; consider Vault's
  database secrets engine (ADR-0001).
- **Redis:** managed Redis / Valkey with TLS and HA; credentials per environment in Vault.
- **Logs:** ECS JSON to stdout, `include-body: false`, no log files; keep `/actuator/prometheus`
  off the public ingress.

## Verification status

| Checked | Result |
|---|---|
| Stack start, Vault seeding, `bootRun` with Vault + PostgreSQL, Flyway V1-V3 | Pass on Windows / Docker Desktop |
| Keycloak token accepted; no token → 401 problem+json | Pass on Windows / Docker Desktop |
| Redis cache filled by `/posts` (`demo:v1:directory-posts::1`) | Pass on Windows / Docker Desktop |
| HTTP logging with masking | Pass on Windows / Docker Desktop |
| Account migration constraints, Redis ACL and compose arguments | Tested on PostgreSQL 16 and Redis 7 |
| Account domain rules, masking rules | Unit-checked (26 and 23 cases) |
| Full `./gradlew test` in CI | Not set up yet |
