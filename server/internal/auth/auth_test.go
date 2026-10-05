package auth

import (
	"strings"
	"testing"
	"time"
)

func TestPasswordHash(t *testing.T) {
	hash := HashPassword("correct horse")
	if !strings.HasPrefix(hash, "$argon2id$v=19$m=19456,t=2,p=1$") {
		t.Errorf("hash = %q, want PHC argon2id format", hash)
	}
	if HashPassword("correct horse") == hash {
		t.Error("two hashes of one password are equal: salt is not random")
	}
	for pw, want := range map[string]bool{"correct horse": true, "correct horsE": false, "": false} {
		ok, err := VerifyPassword(pw, hash)
		if err != nil || ok != want {
			t.Errorf("VerifyPassword(%q) = %v, %v; want %v", pw, ok, err, want)
		}
	}
	if _, err := VerifyPassword("x", "$2a$10$bcrypt"); err == nil {
		t.Error("VerifyPassword accepted a non-argon2id hash")
	}
}

func TestSecretHashIgnoresCaseAndSpaces(t *testing.T) {
	secret, hash := NewSecret()
	if len(secret) != 26 {
		t.Errorf("secret length = %d, want 26", len(secret))
	}
	if string(HashSecret(" "+strings.ToLower(secret)+"\n")) != string(hash) {
		t.Error("hash depends on case or surrounding spaces")
	}
}

func TestLimiter(t *testing.T) {
	now := time.Unix(0, 0)
	l := NewLimiter(2, time.Minute)
	l.now = func() time.Time { return now }

	l.Fail("k")
	if blocked, _ := l.Blocked("k"); blocked {
		t.Fatal("blocked after 1 of 2 failures")
	}
	l.Fail("k")
	if blocked, retry := l.Blocked("k"); !blocked || retry != time.Minute {
		t.Fatalf("Blocked = %v, %v; want true, 1m", blocked, retry)
	}
	if blocked, _ := l.Blocked("other"); blocked {
		t.Fatal("keys are not independent")
	}

	now = now.Add(time.Minute)
	if blocked, _ := l.Blocked("k"); blocked {
		t.Fatal("still blocked after the window")
	}

	l.Fail("k")
	l.Fail("k")
	l.Reset("k")
	if blocked, _ := l.Blocked("k"); blocked {
		t.Fatal("blocked after Reset")
	}
}
