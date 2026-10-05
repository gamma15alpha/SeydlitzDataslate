package session

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbtest"
)

func newUser(t *testing.T) (*dbq.Queries, dbq.User) {
	t.Helper()
	q := dbq.New(dbtest.New(t))
	u, err := q.CreateUser(context.Background(), dbq.CreateUserParams{Login: "player", DisplayName: "player", PasswordHash: auth.HashPassword("player-password")})
	if err != nil {
		t.Fatal(err)
	}
	return q, u
}

func mustAuth(t *testing.T, q *dbq.Queries, token string, now time.Time) Auth {
	t.Helper()
	a, err := Authenticate(context.Background(), q, token, now)
	if err != nil {
		t.Fatalf("authenticate: %v", err)
	}
	return a
}

func TestCreateAndAuthenticate(t *testing.T) {
	ctx := context.Background()
	q, u := newUser(t)
	token, err := Create(ctx, q, u.ID, strings.Repeat("x", 300))
	if err != nil {
		t.Fatal(err)
	}

	a := mustAuth(t, q, token, time.Now())
	if a.User.ID != u.ID || a.NewToken != "" || a.Extended {
		t.Errorf("fresh session: %+v", a)
	}
	list, err := List(ctx, q, u.ID, a.SessionID, time.Now())
	if err != nil || len(list) != 1 || !list[0].Current || len(list[0].UserAgent) != maxUserAgent {
		t.Errorf("list = %+v, %v", list, err)
	}

	if _, err := Authenticate(ctx, q, "not-a-token", time.Now()); !errors.Is(err, ErrInvalid) {
		t.Errorf("unknown token: err = %v, want ErrInvalid", err)
	}
	// Абсолютный предел от входа: дальше сессия не принимается, даже если её продлевали.
	if _, err := Authenticate(ctx, q, token, time.Now().Add(MaxAge+time.Hour)); !errors.Is(err, ErrInvalid) {
		t.Errorf("after MaxAge: err = %v, want ErrInvalid", err)
	}
}

// Ротация и повторное использование старого токена — со своими часами, без правки базы.
// Отметки в базе ставит PostgreSQL (now()), часы теста сдвигают только «сейчас» запроса:
// сутки спустя — ротация, дальше — снова настоящее время.
func TestRotationAndReuse(t *testing.T) {
	ctx := context.Background()
	q, u := newUser(t)
	t0, err := Create(ctx, q, u.ID, "")
	if err != nil {
		t.Fatal(err)
	}

	t1 := mustAuth(t, q, t0, time.Now().Add(rotateEvery+time.Hour)).NewToken
	if t1 == "" {
		t.Fatal("token not rotated after a day")
	}
	if a := mustAuth(t, q, t1, time.Now()); a.NewToken != "" {
		t.Errorf("new token rotated again: %q", a.NewToken)
	}
	mustAuth(t, q, t0, time.Now()) // параллельный запрос со старым — в пределах rotationGrace

	if _, err := Authenticate(ctx, q, t0, time.Now().Add(rotationGrace+time.Minute)); !errors.Is(err, ErrRevoked) {
		t.Fatalf("old token after grace: err = %v, want ErrRevoked", err)
	}
	if _, err := Authenticate(ctx, q, t1, time.Now()); !errors.Is(err, ErrInvalid) {
		t.Errorf("new token after revocation: err = %v, want ErrInvalid", err)
	}
}

func TestExtension(t *testing.T) {
	q, u := newUser(t)
	token, err := Create(context.Background(), q, u.ID, "")
	if err != nil {
		t.Fatal(err)
	}
	later := time.Now().Add(TTL - refreshBelow + time.Hour) // до конца — меньше refreshBelow
	a := mustAuth(t, q, token, later)
	if !a.Extended || !a.ExpiresAt.Equal(later.Add(TTL)) {
		t.Errorf("extended = %v, expiresAt = %v, want %v", a.Extended, a.ExpiresAt, later.Add(TTL))
	}
}

func TestEnd(t *testing.T) {
	ctx := context.Background()
	q, u := newUser(t)
	keep, _ := Create(ctx, q, u.ID, "")
	other, _ := Create(ctx, q, u.ID, "")
	third, _ := Create(ctx, q, u.ID, "")
	keepID := mustAuth(t, q, keep, time.Now()).SessionID
	otherID := mustAuth(t, q, other, time.Now()).SessionID

	if err := End(ctx, q, u.ID, otherID); err != nil {
		t.Fatal(err)
	}
	if err := End(ctx, q, u.ID, otherID); !errors.Is(err, ErrNotFound) {
		t.Errorf("second End: err = %v, want ErrNotFound", err)
	}
	if n, err := EndOthers(ctx, q, u.ID, keepID); err != nil || n != 1 {
		t.Errorf("EndOthers = %d, %v; want 1", n, err)
	}
	mustAuth(t, q, keep, time.Now())
	if _, err := Authenticate(ctx, q, third, time.Now()); !errors.Is(err, ErrInvalid) {
		t.Errorf("ended session: err = %v, want ErrInvalid", err)
	}
}
