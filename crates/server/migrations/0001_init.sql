CREATE TABLE families (
    id          UUID PRIMARY KEY,
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parents (
    id            UUID PRIMARY KEY,
    family_id     UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
    token_hash  TEXT PRIMARY KEY,
    parent_id   UUID NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE children (
    id           UUID PRIMARY KEY,
    family_id    UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX children_family_idx ON children(family_id) WHERE deleted_at IS NULL;

CREATE TABLE devices (
    id           UUID PRIMARY KEY,
    family_id    UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id     UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    platform     TEXT NOT NULL,
    model        TEXT NOT NULL,
    label        TEXT NOT NULL,
    token_hash   TEXT NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX devices_child_idx ON devices(child_id);

CREATE TABLE enrollments (
    id          UUID PRIMARY KEY,
    family_id   UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id    UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    code_hash   TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE packages (
    family_id  UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    package    TEXT NOT NULL,
    label      TEXT NOT NULL,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (family_id, package)
);

CREATE TABLE usage_hours (
    device_id     UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    package       TEXT NOT NULL,
    hour_start    TIMESTAMPTZ NOT NULL,
    tz            TEXT NOT NULL,
    foreground_ms BIGINT NOT NULL,
    launch_count  INTEGER NOT NULL,
    computed_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (device_id, package, hour_start)
);
CREATE INDEX usage_hours_window_idx ON usage_hours(device_id, hour_start);

CREATE TABLE device_hours (
    device_id    UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    hour_start   TIMESTAMPTZ NOT NULL,
    tz           TEXT NOT NULL,
    screen_on_ms BIGINT NOT NULL,
    unlock_count INTEGER NOT NULL,
    computed_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (device_id, hour_start)
);

CREATE TABLE usage_days (
    child_id      UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    package       TEXT NOT NULL,
    day           DATE NOT NULL,
    foreground_ms BIGINT NOT NULL,
    launch_count  INTEGER NOT NULL,
    PRIMARY KEY (child_id, package, day)
);
