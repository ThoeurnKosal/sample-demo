-- Flyway owns the schema; Hibernate only validates it (spring.jpa.hibernate.ddl-auto=validate).
-- Never edit a released migration: add V2__..., V3__... instead.
-- Invariants are enforced here too, so bad data cannot get in through any path.

CREATE TABLE customer (
                          id           UUID                     PRIMARY KEY,
                          version      BIGINT                   NOT NULL,
                          external_id  INTEGER,
                          full_name    VARCHAR(100)             NOT NULL,
                          email        VARCHAR(254)             NOT NULL,
                          phone        VARCHAR(40),
                          city         VARCHAR(100),
                          created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    -- One customer per mailbox. The application stores emails in lower case.
                          CONSTRAINT uq_customer_email          UNIQUE (email),
                          CONSTRAINT ck_customer_email_lower    CHECK (email = lower(email)),
    -- One customer per directory user; also makes concurrent imports safe.
    -- NULLs (customers registered directly) are allowed many times in PostgreSQL.
                          CONSTRAINT uq_customer_external_id    UNIQUE (external_id),
                          CONSTRAINT ck_customer_external_id    CHECK (external_id IS NULL OR external_id > 0),
                          CONSTRAINT ck_customer_full_name      CHECK (length(trim(full_name)) > 0)
);

-- Supports the paged list endpoint: ORDER BY created_at DESC, id
CREATE INDEX ix_customer_created_at_id ON customer (created_at DESC, id);
