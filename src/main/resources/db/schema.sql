-- JFX SSH Client schema
-- 见 docs/DATABASE.md。所有表使用 IF NOT EXISTS，启动可重复执行。

CREATE TABLE IF NOT EXISTS groups (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    parent_id   INTEGER,
    sort        INTEGER NOT NULL DEFAULT 0,
    create_time TEXT,
    update_time TEXT,
    FOREIGN KEY (parent_id) REFERENCES groups (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS connections (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL,
    host             TEXT    NOT NULL,
    port             INTEGER NOT NULL DEFAULT 22,
    username         TEXT,
    auth_type        TEXT    NOT NULL,            -- PASSWORD / PRIVATE_KEY
    password_enc     TEXT,                        -- Base64(iv||ciphertext||tag), AES-256-GCM
    private_key_path TEXT,
    group_id         INTEGER,
    remark           TEXT,
    terminal_type    TEXT,                        -- PTY 类型：xterm-256color / xterm / vt100 / ansi / linux ...
    create_time      TEXT,
    update_time      TEXT,
    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS settings (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    key         TEXT    NOT NULL UNIQUE,
    value       TEXT,
    create_time TEXT,
    update_time TEXT
);

CREATE TABLE IF NOT EXISTS port_forwards (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    connection_id INTEGER NOT NULL,
    name          TEXT    NOT NULL,
    type          TEXT    NOT NULL,            -- LOCAL / REMOTE / DYNAMIC
    bind_host     TEXT    NOT NULL,
    bind_port     INTEGER NOT NULL,
    dest_host     TEXT,
    dest_port     INTEGER,
    auto_start    INTEGER NOT NULL DEFAULT 0,  -- 0=false, 1=true
    create_time   TEXT,
    update_time   TEXT,
    FOREIGN KEY (connection_id) REFERENCES connections (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schema_version (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    version      INTEGER NOT NULL,
    applied_time TEXT
);
