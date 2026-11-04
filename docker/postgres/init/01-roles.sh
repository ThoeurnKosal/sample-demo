#!/bin/bash
# Runs once, on the first start of an empty data volume.
# Creates: schema owner (Flyway/DDL), app user (DML only), schema "demo".
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v owner_user="$DB_OWNER_USER" -v owner_pw="$DB_OWNER_PASSWORD" \
     -v app_user="$DB_APP_USER"     -v app_pw="$DB_APP_PASSWORD" \
     -v db_name="$POSTGRES_DB" <<'SQL'

-- Lock the database down: nobody connects or creates objects by default
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Owner: runs migrations, owns every object
CREATE ROLE :"owner_user" LOGIN PASSWORD :'owner_pw';
GRANT CONNECT ON DATABASE :"db_name" TO :"owner_user";
CREATE SCHEMA demo AUTHORIZATION :"owner_user";
ALTER ROLE :"owner_user" SET search_path = demo;

-- App: read/write data only, cannot ALTER/DROP/CREATE
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_pw';
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
GRANT USAGE ON SCHEMA demo TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner_user" IN SCHEMA demo
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner_user" IN SCHEMA demo
  GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";

-- Server-enforced guard rails for the app user
ALTER ROLE :"app_user" SET search_path = demo;
ALTER ROLE :"app_user" SET statement_timeout = '30s';
ALTER ROLE :"app_user" SET lock_timeout = '10s';
ALTER ROLE :"app_user" SET idle_in_transaction_session_timeout = '60s';
SQL

echo "Roles ${DB_OWNER_USER} and ${DB_APP_USER} created."
