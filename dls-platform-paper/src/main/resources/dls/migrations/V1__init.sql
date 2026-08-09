-- Dig Like Steve - schéma initial (DAT §11)

CREATE TABLE IF NOT EXISTS game_session (
    id          TEXT PRIMARY KEY,
    seed        INTEGER NOT NULL,
    world       TEXT    NOT NULL,
    bounds      TEXT    NOT NULL,
    phase       TEXT    NOT NULL,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    ended_at    INTEGER
);

CREATE INDEX IF NOT EXISTS ix_session_phase ON game_session (phase);

CREATE TABLE IF NOT EXISTS participant (
    session_id  TEXT    NOT NULL,
    player_uuid TEXT    NOT NULL,
    status      TEXT    NOT NULL,
    score       INTEGER NOT NULL DEFAULT 0,
    rank        INTEGER NOT NULL DEFAULT -1,
    updated_at  INTEGER NOT NULL,
    PRIMARY KEY (session_id, player_uuid),
    FOREIGN KEY (session_id) REFERENCES game_session (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS inventory_snapshot (
    id             TEXT PRIMARY KEY,
    player_uuid    TEXT    NOT NULL,
    session_id     TEXT    NOT NULL,
    payload        BLOB    NOT NULL,
    checksum       INTEGER NOT NULL,
    format_version INTEGER NOT NULL,
    state          TEXT    NOT NULL,
    captured_at    INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL
);

-- Interdit structurellement deux gardes actives pour le même joueur (DAT §8.2).
CREATE UNIQUE INDEX IF NOT EXISTS ux_snapshot_active
    ON inventory_snapshot (player_uuid)
    WHERE state NOT IN ('RESTORED', 'INVALIDATED');

CREATE INDEX IF NOT EXISTS ix_snapshot_state ON inventory_snapshot (state);

CREATE TABLE IF NOT EXISTS reward_outbox (
    id              TEXT PRIMARY KEY,
    session_id      TEXT    NOT NULL,
    player_uuid     TEXT    NOT NULL,
    idempotency_key TEXT    NOT NULL UNIQUE,
    strategy        TEXT    NOT NULL,
    payload         TEXT,
    state           TEXT    NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL,
    last_error      TEXT
);

CREATE INDEX IF NOT EXISTS ix_outbox_state ON reward_outbox (state);
CREATE INDEX IF NOT EXISTS ix_outbox_player ON reward_outbox (player_uuid, state);

CREATE TABLE IF NOT EXISTS player_stats (
    player_uuid TEXT PRIMARY KEY,
    games       INTEGER NOT NULL DEFAULT 0,
    wins        INTEGER NOT NULL DEFAULT 0,
    best_score  INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL
);
