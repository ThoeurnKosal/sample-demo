#!/bin/sh
# Seeds the local Vault: KV secrets, a least-privilege policy, and an AppRole.
# Idempotent - safe to re-run with `docker compose up vault-init`.
set -eu

echo "Waiting for Vault at ${VAULT_ADDR}..."
until vault status >/dev/null 2>&1; do sleep 1; done

# --- 1. Secrets ------------------------------------------------------------
# KV v2 is mounted at secret/ in dev mode. Keys are Spring property names,
# so Spring Cloud Vault maps them straight onto the datasource/flyway config.
vault kv put "secret/${APP_NAME}" \
  spring.datasource.username="${DB_APP_USER}" \
  spring.datasource.password="${DB_APP_PASSWORD}" \
  spring.flyway.user="${DB_OWNER_USER}" \
  spring.flyway.password="${DB_OWNER_PASSWORD}" \
  spring.data.redis.username="${REDIS_APP_USER}" \
  spring.data.redis.password="${REDIS_APP_PASSWORD}"

# --- 2. Policy: read-only, this app's paths only ---------------------------
vault policy write "${APP_NAME}-read" - <<EOF
path "secret/data/${APP_NAME}"     { capabilities = ["read"] }
path "secret/data/${APP_NAME}/*"   { capabilities = ["read"] }
path "secret/data/application"     { capabilities = ["read"] }
path "secret/data/application/*"   { capabilities = ["read"] }
EOF

# --- 3. AppRole: how the app logs in (never the root token) -----------------
vault auth list -format=json | grep -q '"approle/"' || vault auth enable approle

vault write "auth/approle/role/${APP_NAME}" \
  token_policies="${APP_NAME}-read" \
  token_ttl=1h \
  token_max_ttl=24h \
  secret_id_ttl=0 \
  secret_id_num_uses=0

# Fixed IDs so local config is predictable. In real environments, let Vault
# generate them and deliver the secret_id via CI (response-wrapped).
vault write "auth/approle/role/${APP_NAME}/role-id" role_id="${VAULT_APP_ROLE_ID}"
vault write "auth/approle/role/${APP_NAME}/custom-secret-id" \
  secret_id="${VAULT_APP_SECRET_ID}" >/dev/null 2>&1 || true

# --- 4. Smoke test: log in as the app and read the secret -------------------
APP_TOKEN=$(vault write -field=token auth/approle/login \
  role_id="${VAULT_APP_ROLE_ID}" secret_id="${VAULT_APP_SECRET_ID}")
VAULT_TOKEN="${APP_TOKEN}" vault kv get -field=spring.datasource.username "secret/${APP_NAME}" >/dev/null

echo "Vault seeded: secret/${APP_NAME}, policy ${APP_NAME}-read, approle ${APP_NAME}."
