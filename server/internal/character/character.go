// Package character — анкеты пользователей (D68): файл анкеты целиком, ревизия для If-Match, надгробия, курсор изменений.
// Лист не разбирается — его формат и миграции у клиента (ядро shared); сервер проверяет только конверт.
package character

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

const (
	MaxBytes = 256 << 10
	// Прошлых ревизий на анкету.
	KeepRevisions = 20
)

var (
	ErrNotFound = errors.New("character not found")
	// ErrIDTaken — анкета с этим id есть у другого пользователя (один файл импортировали двое).
	ErrIDTaken = errors.New("character id belongs to another user")
	ErrInvalid = errors.New("body is not a character file or its id differs from the path")
)

type Character struct {
	ID        uuid.UUID
	Revision  int64
	Data      []byte // nil — удалена
	UpdatedAt time.Time
}

// ConflictError — правка основана не на текущей ревизии (или анкета уже есть при создании); Current — версия сервера.
type ConflictError struct {
	Current Character
}

func (e *ConflictError) Error() string { return "character was changed on the server" }

type Service struct {
	pool *pgxpool.Pool
	q    *dbq.Queries
}

func New(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, q: dbq.New(pool)}
}

func fromRow(c dbq.Character) Character {
	return Character{ID: c.ID, Revision: c.Revision, Data: c.Data, UpdatedAt: c.UpdatedAt}
}

// Validate — конверт из schemas/character.schema.json, ровно настолько, чтобы не хранить чужое.
func Validate(id uuid.UUID, data []byte) error {
	var envelope struct {
		Format string `json:"format"`
		ID     string `json:"id"`
	}
	if json.Unmarshal(data, &envelope) != nil || envelope.Format != "seydlitz.character" {
		return ErrInvalid
	}
	if parsed, err := uuid.Parse(envelope.ID); err != nil || parsed != id {
		return ErrInvalid
	}
	return nil
}

// Changes — анкеты владельца, изменённые после курсора since, и новый курсор.
// Курсор новее известного серверу (база пересоздана) — как с нуля.
func (s *Service) Changes(ctx context.Context, owner uuid.UUID, since int64) (int64, []Character, error) {
	tx, err := s.pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.RepeatableRead, AccessMode: pgx.ReadOnly})
	if err != nil {
		return 0, nil, err
	}
	defer tx.Rollback(ctx)
	q := s.q.WithTx(tx)
	until, err := q.GetCharacterSeq(ctx, owner)
	if err != nil {
		return 0, nil, err
	}
	if since > until {
		since = 0
	}
	rows, err := q.ListCharacterChanges(ctx, dbq.ListCharacterChangesParams{OwnerID: owner, Since: since, Until: until})
	if err != nil {
		return 0, nil, err
	}
	list := make([]Character, 0, len(rows))
	for _, row := range rows {
		list = append(list, fromRow(row))
	}
	return until, list, nil
}

// Get отдаёт и надгробие: клиенту важно знать, что анкету удалили.
func (s *Service) Get(ctx context.Context, owner, id uuid.UUID) (Character, error) {
	row, err := s.q.GetCharacter(ctx, id)
	if errors.Is(err, pgx.ErrNoRows) || err == nil && row.OwnerID != owner {
		return Character{}, ErrNotFound
	}
	if err != nil {
		return Character{}, err
	}
	return fromRow(row), nil
}

// Put записывает анкету. base 0 — создать (If-None-Match: *), иначе — ревизия, на которой основана правка (If-Match).
func (s *Service) Put(ctx context.Context, owner, id uuid.UUID, base int64, data []byte) (Character, error) {
	if err := Validate(id, data); err != nil {
		return Character{}, err
	}
	return s.write(ctx, owner, id, base, data)
}

// Delete оставляет надгробие: удаление дойдёт до других устройств.
func (s *Service) Delete(ctx context.Context, owner, id uuid.UUID, base int64) (Character, error) {
	return s.write(ctx, owner, id, base, nil)
}

func (s *Service) write(ctx context.Context, owner, id uuid.UUID, base int64, data []byte) (Character, error) {
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return Character{}, err
	}
	defer tx.Rollback(ctx)
	q := s.q.WithTx(tx)

	// Сначала пользователь, потом анкета — один порядок блокировок во всех записях.
	seq, err := q.NextCharacterSeq(ctx, owner)
	if err != nil {
		return Character{}, err
	}
	cur, err := q.GetCharacterForUpdate(ctx, id)
	var row dbq.Character
	switch {
	case errors.Is(err, pgx.ErrNoRows):
		// Удалять нечего; правка того, чего нет (база пересоздана), — клиент создаст заново.
		if base != 0 || data == nil {
			return Character{}, ErrNotFound
		}
		row, err = q.InsertCharacter(ctx, dbq.InsertCharacterParams{ID: id, OwnerID: owner, Seq: seq, Data: data})
		if db.IsUniqueViolation(err) {
			return Character{}, ErrIDTaken // тот же id параллельно создал другой пользователь
		}
		if err != nil {
			return Character{}, err
		}
	case err != nil:
		return Character{}, err
	case cur.OwnerID != owner:
		return Character{}, ErrIDTaken
	case cur.Revision != base:
		return Character{}, &ConflictError{Current: fromRow(cur)}
	case cur.Data == nil && data == nil:
		return fromRow(cur), nil // уже удалена
	default:
		if err := q.SaveCharacterRevision(ctx, dbq.SaveCharacterRevisionParams{
			CharacterID: cur.ID, Revision: cur.Revision, Data: cur.Data, UpdatedAt: cur.UpdatedAt,
		}); err != nil {
			return Character{}, err
		}
		if err := q.TrimCharacterRevisions(ctx, dbq.TrimCharacterRevisionsParams{CharacterID: cur.ID, UpTo: cur.Revision - KeepRevisions}); err != nil {
			return Character{}, err
		}
		row, err = q.UpdateCharacter(ctx, dbq.UpdateCharacterParams{ID: id, Seq: seq, Data: data})
		if err != nil {
			return Character{}, err
		}
	}
	return fromRow(row), tx.Commit(ctx)
}
