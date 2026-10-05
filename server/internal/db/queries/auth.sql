-- name: CreateUser :one
INSERT INTO users (login, display_name, password_hash, is_admin)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetUserByLogin :one
SELECT * FROM users WHERE lower(login) = lower(@login);

-- Вызывать в одной транзакции с CreateUser.
-- name: ClaimInvite :one
UPDATE invites SET used_at = now()
WHERE code_hash = $1 AND used_at IS NULL AND expires_at > now()
RETURNING id;

-- name: SetInviteUser :exec
UPDATE invites SET used_by = $2 WHERE id = $1;

-- name: CreateInvite :one
INSERT INTO invites (code_hash, created_by, expires_at)
VALUES ($1, $2, $3)
RETURNING id, created_at, expires_at;

-- name: ListInvites :many
SELECT i.id, i.created_at, i.expires_at, i.used_at, u.login AS used_by_login
FROM invites i
LEFT JOIN users u ON u.id = i.used_by
ORDER BY i.created_at DESC;

-- Использованные остаются как история.
-- name: DeleteUnusedInvite :execrows
DELETE FROM invites WHERE id = $1 AND used_at IS NULL;

-- name: CreateSession :exec
INSERT INTO sessions (token_hash, user_id, expires_at, user_agent)
VALUES ($1, $2, $3, $4);

-- name: GetSessionUser :one
SELECT sqlc.embed(users), sessions.expires_at AS session_expires_at
FROM sessions
JOIN users ON users.id = sessions.user_id
WHERE sessions.token_hash = $1 AND sessions.expires_at > now();

-- name: ExtendSession :exec
UPDATE sessions SET expires_at = $2 WHERE token_hash = $1;

-- name: DeleteSession :exec
DELETE FROM sessions WHERE token_hash = $1;

-- name: DeleteExpiredSessions :execrows
DELETE FROM sessions WHERE expires_at <= now();
