-- Картинки по SHA-256 содержимого (D63). Ссылки — без каскада: очистка, не знающая о ссылке, падает, а не удаляет.

-- +goose Up
CREATE TABLE blobs (
    sha256     bytea PRIMARY KEY CHECK (length(sha256) = 32),
    mime       text NOT NULL,
    bytes      bytea NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX blobs_created_at_idx ON blobs (created_at);

ALTER TABLE users ADD COLUMN avatar_sha256 bytea REFERENCES blobs (sha256);
CREATE INDEX users_avatar_sha256_idx ON users (avatar_sha256);

-- +goose Down
ALTER TABLE users DROP COLUMN avatar_sha256;
DROP TABLE blobs;
