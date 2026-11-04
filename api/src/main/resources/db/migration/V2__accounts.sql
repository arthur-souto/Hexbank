CREATE TABLE accounts.accounts (
                                   id          UUID        PRIMARY KEY,
                                   owner_id    UUID        NOT NULL,
                                   currency    VARCHAR(3)  NOT NULL CHECK (currency IN ('BRL')),
                                   status      VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
                                   created_at  TIMESTAMPTZ NOT NULL,
                                   closed_at   TIMESTAMPTZ,
                                   version     BIGINT      NOT NULL DEFAULT 0,
                                   CONSTRAINT closed_at_matches_status CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);
CREATE INDEX idx_accounts_owner ON accounts.accounts (owner_id);