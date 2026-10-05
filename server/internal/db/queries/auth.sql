-- name: CreateUser :one
INSERT INTO users (login, display_name, password_hash, is_admin)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetUserByLogin :one
SELECT * FROM users WHERE lower(login) = lower(@login);

-- name: UpdateUserLogin :one
UPDATE users SET login = @login WHERE id = @id
RETURNING *;

-- name: UpdateUserPassword :exec
UPDATE users SET password_hash = @password_hash WHERE id = @id;

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

-- name: FindSession :one
SELECT sqlc.embed(users),
       sessions.id AS session_id,
       sessions.created_at AS session_created_at,
       sessions.expires_at AS session_expires_at,
       sessions.rotated_at,
       sessions.confirmed_at,
       sessions.last_used_at,
       sessions.token_hash = @token_hash AS is_current
FROM sessions
JOIN users ON users.id = sessions.user_id
WHERE (sessions.token_hash = @token_hash OR sessions.prev_token_hash = @token_hash)
  AND sessions.expires_at > now() AND sessions.created_at > @created_after;

-- Условие на token_hash: из параллельных запросов ротирует один.
-- name: RotateSession :execrows
UPDATE sessions
SET prev_token_hash = token_hash, token_hash = @new_hash, rotated_at = now(), confirmed_at = NULL
WHERE id = @id AND token_hash = @current_hash;

-- Ответ с новым токеном потерялся: клиент пришёл со старым — выдаём ещё один.
-- name: ReissueSession :execrows
UPDATE sessions SET token_hash = @new_hash, rotated_at = now()
WHERE id = @id AND prev_token_hash = @prev_hash AND confirmed_at IS NULL AND rotated_at < @reissue_before;

-- name: ConfirmSession :exec
UPDATE sessions SET confirmed_at = now() WHERE id = $1 AND confirmed_at IS NULL;

-- name: TouchSession :exec
UPDATE sessions SET last_used_at = now(), expires_at = @expires_at WHERE id = @id;

-- name: DeleteSession :exec
DELETE FROM sessions WHERE id = $1;

-- name: ListUserSessions :many
SELECT id, created_at, last_used_at, user_agent
FROM sessions
WHERE user_id = @user_id AND expires_at > now() AND created_at > @created_after
ORDER BY last_used_at DESC;

-- name: DeleteUserSession :execrows
DELETE FROM sessions WHERE id = @id AND user_id = @user_id;

-- name: DeleteOtherSessions :execrows
DELETE FROM sessions WHERE user_id = @user_id AND id <> @keep_id;

-- name: DeleteExpiredSessions :execrows
DELETE FROM sessions WHERE expires_at <= now();
