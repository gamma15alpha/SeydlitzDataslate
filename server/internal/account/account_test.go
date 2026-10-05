package account

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbtest"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/invite"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/session"
)

var client = Client{IP: "192.0.2.1", UserAgent: "test"}

func wantInvalid(t *testing.T, err error, code string) {
	t.Helper()
	var invalid *InvalidError
	if !errors.As(err, &invalid) || invalid.Code != code {
		t.Errorf("err = %v, want InvalidError %s", err, code)
	}
}

func TestRegister(t *testing.T) {
	ctx := context.Background()
	s := New(dbtest.New(t))
	admin, err := s.CreateAdmin(ctx, "admin", "admin-password")
	if err != nil {
		t.Fatal(err)
	}
	_, code, err := invite.Create(ctx, s.q, admin.ID, invite.DefaultDays)
	if err != nil {
		t.Fatal(err)
	}

	// Правила — по порядку: сначала логин.
	_, _, err = s.Register(ctx, RegisterInput{Invite: code, Login: "x", Password: "short"}, client)
	wantInvalid(t, err, "invalid_login")
	_, _, err = s.Register(ctx, RegisterInput{Invite: code, Login: "player", Password: "short"}, client)
	wantInvalid(t, err, "invalid_password")

	// Занятый логин не тратит инвайт: транзакция откатывается.
	if _, _, err := s.Register(ctx, RegisterInput{Invite: code, Login: "ADMIN", Password: "player-password"}, client); !errors.Is(err, ErrLoginTaken) {
		t.Fatalf("taken login: err = %v, want ErrLoginTaken", err)
	}
	user, token, err := s.Register(ctx, RegisterInput{Invite: code, Login: " player ", Password: "player-password"}, client)
	if err != nil {
		t.Fatal(err)
	}
	if user.Login != "player" || user.DisplayName != "player" || user.IsAdmin {
		t.Errorf("user = %+v", user)
	}
	if a, err := session.Authenticate(ctx, s.q, token, time.Now()); err != nil || a.User.ID != user.ID {
		t.Errorf("session of registered user: %+v, %v", a, err)
	}

	if _, _, err := s.Register(ctx, RegisterInput{Invite: code, Login: "second", Password: "second-password"}, client); !errors.Is(err, invite.ErrInvalid) {
		t.Errorf("reused invite: err = %v, want invite.ErrInvalid", err)
	}
}

func TestLoginLimits(t *testing.T) {
	ctx := context.Background()
	s := New(dbtest.New(t))
	if _, err := s.CreateAdmin(ctx, "Admin", "admin-password"); err != nil {
		t.Fatal(err)
	}
	if _, _, err := s.Login(ctx, "admin", "admin-password", client); err != nil {
		t.Fatalf("login is case-insensitive: %v", err)
	}
	for range 10 {
		if _, _, err := s.Login(ctx, "admin", "wrong-password", client); !errors.Is(err, ErrInvalidCredentials) {
			t.Fatalf("err = %v, want ErrInvalidCredentials", err)
		}
	}
	_, _, err := s.Login(ctx, "admin", "admin-password", Client{IP: "192.0.2.2"})
	var tooMany *TooManyAttemptsError
	if !errors.As(err, &tooMany) || tooMany.RetryAfter <= 0 {
		t.Errorf("after 10 failures from any IP: err = %v, want TooManyAttemptsError", err)
	}
}

func TestChangePasswordEndsOtherSessions(t *testing.T) {
	ctx := context.Background()
	s := New(dbtest.New(t))
	user, err := s.CreateAdmin(ctx, "admin", "admin-password")
	if err != nil {
		t.Fatal(err)
	}
	_, current, _ := s.Login(ctx, "admin", "admin-password", client)
	_, other, _ := s.Login(ctx, "admin", "admin-password", client)
	cur, err := session.Authenticate(ctx, s.q, current, time.Now())
	if err != nil {
		t.Fatal(err)
	}

	if err := s.ChangePassword(ctx, user, cur.SessionID, "wrong-password", "new-password", client); !errors.Is(err, ErrWrongPassword) {
		t.Fatalf("err = %v, want ErrWrongPassword", err)
	}
	if err := s.ChangePassword(ctx, user, cur.SessionID, "admin-password", "new-password", client); err != nil {
		t.Fatal(err)
	}
	if _, err := session.Authenticate(ctx, s.q, current, time.Now()); err != nil {
		t.Errorf("current session ended: %v", err)
	}
	if _, err := session.Authenticate(ctx, s.q, other, time.Now()); !errors.Is(err, session.ErrInvalid) {
		t.Errorf("other session: err = %v, want ErrInvalid", err)
	}
	if _, _, err := s.Login(ctx, "admin", "new-password", client); err != nil {
		t.Errorf("login with new password: %v", err)
	}
}

func TestCreateAdmin(t *testing.T) {
	ctx := context.Background()
	s := New(dbtest.New(t))
	_, err := s.CreateAdmin(ctx, "a", "admin-password")
	wantInvalid(t, err, "invalid_login")
	user, err := s.CreateAdmin(ctx, "admin", "admin-password")
	if err != nil || !user.IsAdmin {
		t.Fatalf("CreateAdmin = %+v, %v", user, err)
	}
	if _, err := s.CreateAdmin(ctx, "Admin", "admin-password"); !errors.Is(err, ErrLoginTaken) {
		t.Errorf("duplicate: err = %v, want ErrLoginTaken", err)
	}
}
