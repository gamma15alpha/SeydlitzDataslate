// Package session — сессии по случайному токену: создание, проверка с продлением и ротацией, завершение.
// Про HTTP не знает: токен на входе, результат на выходе; cookie и заголовки — забота api.
// Функции принимают *dbq.Queries — их можно звать и внутри чужой транзакции.
package session

import (
	"context"
	"errors"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/logging"
)

const (
	TTL = 30 * 24 * time.Hour
	// Продлеваем, только когда осталось меньше, — не пишем в базу на каждый запрос.
	refreshBelow = 15 * 24 * time.Hour
	// Абсолютный предел от входа (OWASP): дальше продления нет.
	MaxAge        = 90 * 24 * time.Hour
	rotateEvery   = 24 * time.Hour
	rotationGrace = time.Minute
	touchEvery    = time.Hour
	maxUserAgent  = 256
)

var (
	ErrInvalid = errors.New("session expired or invalid")
	// ErrRevoked — старый токен пришёл после ротации: им пользуется кто-то ещё, сессия отозвана.
	ErrRevoked  = errors.New("session revoked: token reuse detected")
	ErrNotFound = errors.New("session not found")
)

// Create открывает сессию и возвращает её токен; в базе — только хеш.
func Create(ctx context.Context, q *dbq.Queries, userID uuid.UUID, userAgent string) (string, error) {
	token, hash := auth.NewSecret()
	if len(userAgent) > maxUserAgent {
		userAgent = userAgent[:maxUserAgent]
	}
	err := q.CreateSession(ctx, dbq.CreateSessionParams{
		TokenHash: hash,
		UserID:    userID,
		ExpiresAt: time.Now().Add(TTL),
		UserAgent: userAgent,
	})
	return token, err
}

type Auth struct {
	User      dbq.User
	SessionID uuid.UUID
	// NewToken — токен сменился при ротации; клиенту нужно отдать новый. Пусто — не сменился.
	NewToken string
	// ExpiresAt — срок сессии после этого запроса; Extended — срок сдвинулся.
	ExpiresAt time.Time
	Extended  bool
}

// Authenticate проверяет токен, раз в сутки меняет его и продлевает сессию, пока она используется.
// Ошибки: ErrInvalid, ErrRevoked или ошибка базы.
func Authenticate(ctx context.Context, q *dbq.Queries, token string, now time.Time) (Auth, error) {
	row, err := q.FindSession(ctx, dbq.FindSessionParams{TokenHash: auth.HashSecret(token), CreatedAfter: now.Add(-MaxAge)})
	if errors.Is(err, pgx.ErrNoRows) {
		return Auth{}, ErrInvalid
	}
	if err != nil {
		return Auth{}, err
	}
	if info := logging.Request(ctx); info != nil {
		info.UserID = row.User.ID.String()
	}

	newToken, err := rotate(ctx, q, row, token, now)
	if errors.Is(err, errTokenReuse) {
		slog.WarnContext(ctx, "session token reuse, session revoked", "session_id", row.SessionID)
		return Auth{}, ErrRevoked
	}
	if err != nil {
		return Auth{}, err
	}

	expires := now.Add(TTL)
	if limit := row.SessionCreatedAt.Add(MaxAge); expires.After(limit) {
		expires = limit
	}
	extend := row.SessionExpiresAt.Sub(now) < refreshBelow && expires.After(row.SessionExpiresAt)
	if !extend {
		expires = row.SessionExpiresAt
	}
	if extend || now.Sub(row.LastUsedAt) >= touchEvery {
		if err := q.TouchSession(ctx, dbq.TouchSessionParams{ID: row.SessionID, ExpiresAt: expires}); err != nil {
			return Auth{}, err
		}
	}
	return Auth{User: row.User, SessionID: row.SessionID, NewToken: newToken, ExpiresAt: expires, Extended: extend}, nil
}

var errTokenReuse = errors.New("session token reuse")

// rotate раз в сутки выдаёт новый токен. Старый принимается, пока новый не пришёл ни разу
// (ответ мог потеряться), и ещё rotationGrace после — для параллельных запросов.
// Старый токен позже — его использует кто-то ещё: сессия отзывается.
func rotate(ctx context.Context, q *dbq.Queries, row dbq.FindSessionRow, token string, now time.Time) (string, error) {
	newToken, newHash := auth.NewSecret()
	hash := auth.HashSecret(token)
	if row.IsCurrent {
		if row.ConfirmedAt == nil {
			if err := q.ConfirmSession(ctx, row.SessionID); err != nil {
				return "", err
			}
		}
		if now.Sub(row.RotatedAt) < rotateEvery {
			return "", nil
		}
		n, err := q.RotateSession(ctx, dbq.RotateSessionParams{ID: row.SessionID, NewHash: newHash, CurrentHash: hash})
		return issued(newToken, n, err)
	}
	switch {
	case row.ConfirmedAt == nil:
		n, err := q.ReissueSession(ctx, dbq.ReissueSessionParams{
			ID: row.SessionID, NewHash: newHash, PrevHash: hash, ReissueBefore: now.Add(-rotationGrace),
		})
		return issued(newToken, n, err)
	case now.Sub(*row.ConfirmedAt) < rotationGrace:
		return "", nil
	default:
		if err := q.DeleteSession(ctx, row.SessionID); err != nil {
			return "", err
		}
		return "", errTokenReuse
	}
}

// issued — токен выдан, только если обновление прошло (иначе его уже выдал параллельный запрос).
func issued(token string, rows int64, err error) (string, error) {
	if err != nil || rows == 0 {
		return "", err
	}
	return token, nil
}

type Info struct {
	ID         uuid.UUID
	CreatedAt  time.Time
	LastUsedAt time.Time // с точностью до touchEvery
	UserAgent  string
	Current    bool
}

// List — активные сессии пользователя, последние использованные первыми; current — сессия запроса.
func List(ctx context.Context, q *dbq.Queries, userID, current uuid.UUID, now time.Time) ([]Info, error) {
	rows, err := q.ListUserSessions(ctx, dbq.ListUserSessionsParams{UserID: userID, CreatedAfter: now.Add(-MaxAge)})
	if err != nil {
		return nil, err
	}
	list := make([]Info, 0, len(rows))
	for _, row := range rows {
		list = append(list, Info{ID: row.ID, CreatedAt: row.CreatedAt, LastUsedAt: row.LastUsedAt, UserAgent: row.UserAgent, Current: row.ID == current})
	}
	return list, nil
}

// End завершает сессию пользователя; чужую — ErrNotFound, как и несуществующую.
func End(ctx context.Context, q *dbq.Queries, userID, id uuid.UUID) error {
	n, err := q.DeleteUserSession(ctx, dbq.DeleteUserSessionParams{ID: id, UserID: userID})
	if err != nil {
		return err
	}
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

// EndOthers завершает все сессии пользователя, кроме keep; возвращает, сколько завершено.
func EndOthers(ctx context.Context, q *dbq.Queries, userID, keep uuid.UUID) (int64, error) {
	return q.DeleteOtherSessions(ctx, dbq.DeleteOtherSessionsParams{UserID: userID, KeepID: keep})
}

// Delete — выход: завершает сессию по её ID.
func Delete(ctx context.Context, q *dbq.Queries, id uuid.UUID) error {
	return q.DeleteSession(ctx, id)
}

// Cleanup удаляет истёкшие сессии; возвращает, сколько удалено.
func Cleanup(ctx context.Context, q *dbq.Queries) (int64, error) {
	return q.DeleteExpiredSessions(ctx)
}
