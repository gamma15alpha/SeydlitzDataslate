-- Анкеты (D68): файл анкеты целиком, ревизия для If-Match, надгробия, история прошлых ревизий.
-- users.character_seq — счётчик изменений анкет пользователя: курсор для «изменения после».

-- +goose Up
ALTER TABLE users ADD COLUMN character_seq bigint NOT NULL DEFAULT 0;

CREATE TABLE characters (
    id         uuid PRIMARY KEY, -- из файла анкеты, создаёт клиент
    owner_id   uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    revision   bigint NOT NULL,
    seq        bigint NOT NULL, -- users.character_seq при последнем изменении
    data       jsonb,           -- NULL — удалена (надгробие)
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX characters_owner_seq_idx ON characters (owner_id, seq);

CREATE TABLE character_revisions (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    revision     bigint NOT NULL,
    data         jsonb,
    updated_at   timestamptz NOT NULL,
    PRIMARY KEY (character_id, revision)
);

-- +goose Down
DROP TABLE character_revisions;
DROP TABLE characters;
ALTER TABLE users DROP COLUMN character_seq;
