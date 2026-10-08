-- Блокирует пользователя до конца транзакции: изменения его анкет идут по одному,
-- поэтому seq растёт в порядке коммитов и курсор не перескочит незакоммиченное.
-- name: NextCharacterSeq :one
UPDATE users SET character_seq = character_seq + 1 WHERE id = $1
RETURNING character_seq;

-- name: GetCharacterSeq :one
SELECT character_seq FROM users WHERE id = $1;

-- name: GetCharacter :one
SELECT * FROM characters WHERE id = $1;

-- name: GetCharacterForUpdate :one
SELECT * FROM characters WHERE id = $1 FOR UPDATE;

-- name: InsertCharacter :one
INSERT INTO characters (id, owner_id, revision, seq, data)
VALUES (@id, @owner_id, 1, @seq, @data)
RETURNING *;

-- data NULL — удаление.
-- name: UpdateCharacter :one
UPDATE characters SET revision = revision + 1, seq = @seq, data = sqlc.narg(data), updated_at = now()
WHERE id = @id
RETURNING *;

-- name: SaveCharacterRevision :exec
INSERT INTO character_revisions (character_id, revision, data, updated_at)
VALUES ($1, $2, $3, $4);

-- name: TrimCharacterRevisions :exec
DELETE FROM character_revisions WHERE character_id = @character_id AND revision <= @up_to;

-- Первая загрузка (since = 0) — без надгробий: удалённое клиенту не нужно.
-- name: ListCharacterChanges :many
SELECT * FROM characters
WHERE owner_id = @owner_id AND seq > @since AND seq <= @until AND (@since::bigint > 0 OR data IS NOT NULL)
ORDER BY seq;
