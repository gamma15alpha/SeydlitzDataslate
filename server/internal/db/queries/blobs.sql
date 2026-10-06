-- Повторная загрузка заново отсчитывает срок до очистки.
-- name: PutBlob :exec
INSERT INTO blobs (sha256, mime, bytes)
VALUES ($1, $2, $3)
ON CONFLICT (sha256) DO UPDATE SET created_at = now();

-- name: GetBlob :one
SELECT mime, bytes FROM blobs WHERE sha256 = $1;

-- name: SetUserAvatar :one
UPDATE users SET avatar_sha256 = sqlc.narg(avatar_sha256) WHERE id = @id
RETURNING *;

-- Каждая таблица со ссылкой на blobs — здесь (D63).
-- name: DeleteUnusedBlobs :execrows
DELETE FROM blobs b
WHERE b.created_at < @before
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.avatar_sha256 = b.sha256);
