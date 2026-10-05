-- Пользователи, инвайты на регистрацию, сессии.
-- Коды инвайтов и токены сессий хранятся только как SHA-256: утечка базы не даёт войти.

-- +goose Up
CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    login         text NOT NULL,
    display_name  text NOT NULL,
    password_hash text NOT NULL, -- argon2id в PHC-формате
    is_admin      boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_login_key ON users (lower(login));

CREATE TABLE invites (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    code_hash  bytea NOT NULL UNIQUE,
    created_by uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at    timestamptz, -- признак использования; used_by может обнулиться при удалении пользователя
    used_by    uuid REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE sessions (
    token_hash bytea PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    user_agent text NOT NULL DEFAULT ''
);
CREATE INDEX sessions_user_id_idx ON sessions (user_id);
CREATE INDEX sessions_expires_at_idx ON sessions (expires_at);

-- +goose Down
DROP TABLE sessions;
DROP TABLE invites;
DROP TABLE users;
