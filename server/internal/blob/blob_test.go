package blob

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbtest"
)

func put(t *testing.T, q *dbq.Queries, data string) []byte {
	t.Helper()
	sum, err := Put(context.Background(), q, "image/jpeg", []byte(data))
	if err != nil {
		t.Fatal(err)
	}
	return sum
}

func age(t *testing.T, pool *pgxpool.Pool, sum []byte, by time.Duration) {
	t.Helper()
	if _, err := pool.Exec(context.Background(), "UPDATE blobs SET created_at = created_at - $2::interval WHERE sha256 = $1", sum, by.String()); err != nil {
		t.Fatal(err)
	}
}

func exists(t *testing.T, q *dbq.Queries, sum []byte) bool {
	t.Helper()
	_, err := Get(context.Background(), q, sum)
	if err != nil && !errors.Is(err, ErrNotFound) {
		t.Fatal(err)
	}
	return err == nil
}

func TestCleanupRemovesOnlyOldUnreferenced(t *testing.T) {
	ctx := context.Background()
	pool := dbtest.New(t)
	q := dbq.New(pool)

	orphan := put(t, q, "orphan")
	avatar := put(t, q, "avatar")
	fresh := put(t, q, "fresh")
	age(t, pool, orphan, KeepUnused+time.Hour)
	age(t, pool, avatar, KeepUnused+time.Hour)

	user, err := q.CreateUser(ctx, dbq.CreateUserParams{Login: "player", DisplayName: "player", PasswordHash: "x"})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := q.SetUserAvatar(ctx, dbq.SetUserAvatarParams{ID: user.ID, AvatarSha256: avatar}); err != nil {
		t.Fatal(err)
	}

	n, err := Cleanup(ctx, q, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	if n != 1 {
		t.Errorf("removed %d, want 1", n)
	}
	if exists(t, q, orphan) {
		t.Error("old unreferenced image survived")
	}
	if !exists(t, q, avatar) {
		t.Error("referenced image removed")
	}
	if !exists(t, q, fresh) {
		t.Error("fresh image removed: no grace period for a reference to appear")
	}

	// Аватар убрали — картинка уходит при следующей очистке.
	if _, err := q.SetUserAvatar(ctx, dbq.SetUserAvatarParams{ID: user.ID}); err != nil {
		t.Fatal(err)
	}
	if _, err := Cleanup(ctx, q, time.Now()); err != nil {
		t.Fatal(err)
	}
	if exists(t, q, avatar) {
		t.Error("image of a removed avatar survived")
	}
}

func TestPutAgainRestartsGracePeriod(t *testing.T) {
	ctx := context.Background()
	pool := dbtest.New(t)
	q := dbq.New(pool)

	sum := put(t, q, "same")
	age(t, pool, sum, KeepUnused+time.Hour)
	if again := put(t, q, "same"); string(again) != string(sum) {
		t.Fatal("same content, different hash")
	}
	if _, err := Cleanup(ctx, q, time.Now()); err != nil {
		t.Fatal(err)
	}
	if !exists(t, q, sum) {
		t.Error("re-uploaded image removed")
	}
}
