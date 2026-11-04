-- Adds updated_at: when the customer row was last changed.
-- Safe for rolling deployments and existing data:
--   1. The column is added with DEFAULT now(), so an older app version that does not know
--      the column can still INSERT while both versions run side by side.
--      (PostgreSQL 11+: a constant/stable default does not rewrite the table, so this is instant.)
--   2. Existing rows are back-filled with created_at, which is their real "last change"
--      (the default above would otherwise stamp them with the migration time).

ALTER TABLE customer
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

UPDATE customer
SET updated_at = created_at;

COMMENT ON COLUMN customer.updated_at IS
    'Last modification time (UTC). Set by the application; the default only protects older app versions.';
