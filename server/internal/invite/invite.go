// Package invite — инвайты на регистрацию: создаёт администратор, использует account.Register.
// Функции принимают *dbq.Queries — их можно звать и внутри чужой транзакции.
package invite

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

const (
	DefaultDays = 7
	MaxDays     = 90
)

var (
	ErrInvalidExpiry = fmt.Errorf("expiresInDays must be between 1 and %d", MaxDays)
	ErrNotFound      = errors.New("invite not found or already used")
	// ErrInvalid — кода нет, он истёк или уже использован; что именно — не уточняется.
	ErrInvalid = errors.New("invite is invalid, expired or already used")
)

type Invite struct {
	ID        uuid.UUID
	CreatedAt time.Time
	ExpiresAt time.Time
	UsedAt    *time.Time
	UsedBy    *string // логин; nil — не использован или пользователь удалён
}

// Create возвращает код — единственный раз: хранится только его хеш.
func Create(ctx context.Context, q *dbq.Queries, createdBy uuid.UUID, days int) (Invite, string, error) {
	if days < 1 || days > MaxDays {
		return Invite{}, "", ErrInvalidExpiry
	}
	code, hash := auth.NewSecret()
	row, err := q.CreateInvite(ctx, dbq.CreateInviteParams{
		CodeHash:  hash,
		CreatedBy: createdBy,
		ExpiresAt: time.Now().Add(time.Duration(days) * 24 * time.Hour),
	})
	if err != nil {
		return Invite{}, "", err
	}
	return Invite{ID: row.ID, CreatedAt: row.CreatedAt, ExpiresAt: row.ExpiresAt}, code, nil
}

// List — новые первыми.
func List(ctx context.Context, q *dbq.Queries) ([]Invite, error) {
	rows, err := q.ListInvites(ctx)
	if err != nil {
		return nil, err
	}
	invites := make([]Invite, 0, len(rows))
	for _, row := range rows {
		invites = append(invites, Invite{ID: row.ID, CreatedAt: row.CreatedAt, ExpiresAt: row.ExpiresAt, UsedAt: row.UsedAt, UsedBy: row.UsedByLogin})
	}
	return invites, nil
}

// Revoke удаляет только неиспользованный инвайт: использованные остаются как история.
func Revoke(ctx context.Context, q *dbq.Queries, id uuid.UUID) error {
	n, err := q.DeleteUnusedInvite(ctx, id)
	if err != nil {
		return err
	}
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

// Claim помечает инвайт использованным. Звать в транзакции регистрации: откат вернёт инвайт.
func Claim(ctx context.Context, q *dbq.Queries, code string) (uuid.UUID, error) {
	id, err := q.ClaimInvite(ctx, auth.HashSecret(code))
	if errors.Is(err, pgx.ErrNoRows) {
		return uuid.Nil, ErrInvalid
	}
	return id, err
}

// SetUser — кто зарегистрировался по инвайту; в той же транзакции, что и Claim.
func SetUser(ctx context.Context, q *dbq.Queries, id, userID uuid.UUID) error {
	return q.SetInviteUser(ctx, dbq.SetInviteUserParams{ID: id, UsedBy: &userID})
}
