-- Rivy sync server schema. Safe to run on every start.

-- One row per synced item (an account, a transaction, a category, ...).
-- The server does not interpret `data`: it stores the latest version the
-- devices agreed on and hands out changes in `seq` order.
CREATE TABLE IF NOT EXISTS records (
    owner      text        NOT NULL,
    type       text        NOT NULL,
    id         text        NOT NULL,
    data       jsonb       NOT NULL,
    -- When the item was last edited on a device. Decides who wins a conflict.
    updated_at timestamptz NOT NULL,
    -- Deletions are kept as tombstones so every device learns about them.
    deleted    boolean     NOT NULL DEFAULT false,
    -- Server-side change counter. Devices pull "everything after seq N".
    seq        bigint      NOT NULL,
    PRIMARY KEY (owner, type, id)
);

CREATE INDEX IF NOT EXISTS records_owner_seq_idx ON records (owner, seq);

CREATE SEQUENCE IF NOT EXISTS records_seq;

-- One row per signed-in device. Only a hash of the session token is stored.
CREATE TABLE IF NOT EXISTS sessions (
    token_hash   bytea       PRIMARY KEY,
    email        text        NOT NULL,
    device_name  text        NOT NULL DEFAULT '',
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz NOT NULL DEFAULT now()
);
