#!/usr/bin/env bash
# End-to-end smoke test against a running instance (./gradlew bootRun).
# Usage: scripts/smoke-test.sh [api-base-url] [management-base-url]
set -euo pipefail

API="${1:-http://localhost:8080}/api/v1/customers"
MGMT="${2:-http://localhost:8081}"
EMAIL="smoke.$(date +%s)@example.com"
EMAIL_UPPER=$(printf '%s' "$EMAIL" | tr '[:lower:]' '[:upper:]')
FAILURES=0

body_file=$(mktemp)
trap 'rm -f "$body_file"' EXIT

# request METHOD URL [JSON]  -> prints HTTP status, body goes to $body_file
request() {
  local method=$1 url=$2 data=${3:-}
  if [[ -n "$data" ]]; then
    curl -sS -o "$body_file" -w '%{http_code}' -X "$method" "$url" \
      -H 'Content-Type: application/json' -d "$data"
  else
    curl -sS -o "$body_file" -w '%{http_code}' -X "$method" "$url"
  fi
}

check() {
  local name=$1 expected=$2 actual=$3
  if [[ "$actual" == "$expected" ]]; then
    printf '  PASS  %-45s %s\n' "$name" "$actual"
  else
    printf '  FAIL  %-45s expected %s, got %s: %s\n' "$name" "$expected" "$actual" "$(cat "$body_file")"
    FAILURES=$((FAILURES + 1))
  fi
}

echo "Smoke testing $API"

check "readiness probe (includes db)" 200 "$(request GET "$MGMT/actuator/health/readiness")"

check "create customer" 201 "$(request POST "$API" "{\"name\":\"Smoke Test\",\"email\":\"$EMAIL\"}")"
ID=$(sed -E 's/.*"id":([0-9]+).*/\1/' "$body_file")
[[ "$ID" =~ ^[0-9]+$ ]] || { echo "  FAIL  could not read id from: $(cat "$body_file")"; exit 1; }

check "get customer $ID" 200 "$(request GET "$API/$ID")"
check "duplicate email (different case) -> 409" 409 \
  "$(request POST "$API" "{\"name\":\"Dup\",\"email\":\"$EMAIL_UPPER\"}")"
check "invalid body -> 400" 400 "$(request POST "$API" '{"name":"","email":"nope"}')"
check "update customer $ID" 200 \
  "$(request PUT "$API/$ID" "{\"name\":\"Smoke Updated\",\"email\":\"$EMAIL\"}")"
check "list customers" 200 "$(request GET "$API?page=0&size=5")"
check "page size above limit -> 400" 400 "$(request GET "$API?size=1000")"
check "delete customer $ID" 204 "$(request DELETE "$API/$ID")"
check "get deleted customer -> 404" 404 "$(request GET "$API/$ID")"

if (( FAILURES > 0 )); then
  echo "$FAILURES check(s) failed"
  exit 1
fi
echo "All checks passed"
