package invite

import (
	"context"
	"errors"
	"testing"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbtest"
)

func TestInviteLifecycle(t *testing.T) {
	ctx := context.Background()
	q := dbq.New(dbtest.New(t))
	admin, err := q.CreateUser(ctx, dbq.CreateUserParams{Login: "admin", DisplayName: "admin", PasswordHash: auth.HashPassword("admin-password"), IsAdmin: true})
	if err != nil {
		t.Fatal(err)
	}

	for _, days := range []int{0, MaxDays + 1} {
		if _, _, err := Create(ctx, q, admin.ID, days); !errors.Is(err, ErrInvalidExpiry) {
			t.Errorf("Create(%d days): err = %v, want ErrInvalidExpiry", days, err)
		}
	}
	used, code, err := Create(ctx, q, admin.ID, DefaultDays)
	if err != nil {
		t.Fatal(err)
	}
	unused, _, err := Create(ctx, q, admin.ID, 1)
	if err != nil {
		t.Fatal(err)
	}

	id, err := Claim(ctx, q, code)
	if err != nil || id != used.ID {
		t.Fatalf("Claim = %s, %v; want %s", id, err, used.ID)
	}
	if err := SetUser(ctx, q, id, admin.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := Claim(ctx, q, code); !errors.Is(err, ErrInvalid) {
		t.Errorf("second Claim: err = %v, want ErrInvalid", err)
	}

	list, err := List(ctx, q)
	if err != nil || len(list) != 2 {
		t.Fatalf("List = %+v, %v", list, err)
	}
	for _, i := range list {
		if i.ID == used.ID && (i.UsedAt == nil || i.UsedBy == nil || *i.UsedBy != "admin") {
			t.Errorf("used invite = %+v", i)
		}
	}

	if err := Revoke(ctx, q, used.ID); !errors.Is(err, ErrNotFound) {
		t.Errorf("Revoke(used): err = %v, want ErrNotFound — used invites stay as history", err)
	}
	if err := Revoke(ctx, q, unused.ID); err != nil {
		t.Errorf("Revoke(unused): %v", err)
	}
}
