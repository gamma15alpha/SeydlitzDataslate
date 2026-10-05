-- Ротация токена раз в сутки: prev_token_hash принимается, пока новый токен не подтверждён
-- (confirmed_at), и ещё минуту после — для параллельных запросов. Позже — признак кражи.

-- +goose Up
ALTER TABLE sessions DROP CONSTRAINT sessions_pkey;
ALTER TABLE sessions
    ADD COLUMN id uuid NOT NULL DEFAULT uuidv7(),
    ADD COLUMN prev_token_hash bytea UNIQUE,
    ADD COLUMN rotated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN confirmed_at timestamptz DEFAULT now(),
    ADD COLUMN last_used_at timestamptz NOT NULL DEFAULT now(),
    ADD PRIMARY KEY (id),
    ADD CONSTRAINT sessions_token_hash_key UNIQUE (token_hash);

-- +goose Down
ALTER TABLE sessions DROP CONSTRAINT sessions_pkey;
ALTER TABLE sessions
    DROP CONSTRAINT sessions_token_hash_key,
    DROP COLUMN id,
    DROP COLUMN prev_token_hash,
    DROP COLUMN rotated_at,
    DROP COLUMN confirmed_at,
    DROP COLUMN last_used_at,
    ADD PRIMARY KEY (token_hash);
