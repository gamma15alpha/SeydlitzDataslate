package api

import (
	"context"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func (a *testAPI) exec(sql string) {
	a.t.Helper()
	if _, err := a.pool.Exec(context.Background(), sql); err != nil {
		a.t.Fatal(err)
	}
}

func TestTokenRotationDetectsReuse(t *testing.T) {
	a := newTestAPI(t)
	t0 := a.createUser("player", "player-password", false)

	rec := a.do(http.MethodGet, "/api/me", t0, nil)
	expectStatus(t, rec, http.StatusOK)
	if got := rec.Header().Get("X-Session-Token"); got != "" {
		t.Fatalf("fresh token rotated: %q", got)
	}

	a.exec("UPDATE sessions SET rotated_at = now() - interval '25 hours'")
	rec = a.do(http.MethodGet, "/api/me", t0, nil)
	expectStatus(t, rec, http.StatusOK)
	t1 := rec.Header().Get("X-Session-Token")
	if t1 == "" || t1 == t0 {
		t.Fatalf("rotation token = %q", t1)
	}

	expectStatus(t, a.do(http.MethodGet, "/api/me", t0, nil), http.StatusOK) // t1 ещё не пришёл
	expectStatus(t, a.do(http.MethodGet, "/api/me", t1, nil), http.StatusOK)
	expectStatus(t, a.do(http.MethodGet, "/api/me", t0, nil), http.StatusOK) // параллельный запрос

	a.exec("UPDATE sessions SET confirmed_at = now() - interval '2 minutes'")
	rec = a.do(http.MethodGet, "/api/me", t0, nil)
	expectStatus(t, rec, http.StatusUnauthorized)
	assertJSONError(t, rec, "session_revoked")

	rec = a.do(http.MethodGet, "/api/me", t1, nil)
	expectStatus(t, rec, http.StatusUnauthorized)
	assertJSONError(t, rec, "unauthorized")
}

func TestLostRotationResponse(t *testing.T) {
	a := newTestAPI(t)
	t0 := a.createUser("player", "player-password", false)

	a.exec("UPDATE sessions SET rotated_at = now() - interval '25 hours'")
	lost := a.do(http.MethodGet, "/api/me", t0, nil).Header().Get("X-Session-Token")

	rec := a.do(http.MethodGet, "/api/me", t0, nil)
	expectStatus(t, rec, http.StatusOK)
	if got := rec.Header().Get("X-Session-Token"); got != "" {
		t.Fatalf("reissued within a minute: %q", got)
	}

	a.exec("UPDATE sessions SET rotated_at = now() - interval '2 minutes'")
	rec = a.do(http.MethodGet, "/api/me", t0, nil)
	expectStatus(t, rec, http.StatusOK)
	t2 := rec.Header().Get("X-Session-Token")
	if t2 == "" || t2 == lost {
		t.Fatalf("reissued token = %q", t2)
	}
	expectStatus(t, a.do(http.MethodGet, "/api/me", lost, nil), http.StatusUnauthorized)
	expectStatus(t, a.do(http.MethodGet, "/api/me", t2, nil), http.StatusOK)
}

func TestCookieRotation(t *testing.T) {
	a := newTestAPI(t)
	a.createUser("player", "player-password", false)
	srv := httptest.NewTLSServer(a.h)
	t.Cleanup(srv.Close)
	client := srv.Client()
	client.Jar, _ = cookiejar.New(nil)

	resp, err := client.Post(srv.URL+"/api/auth/login", "application/json", strings.NewReader(`{"login":"player","password":"player-password"}`))
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	t0 := resp.Cookies()[0].Value

	a.exec("UPDATE sessions SET rotated_at = now() - interval '25 hours'")
	resp, err = client.Get(srv.URL + "/api/me")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.Header.Get("X-Session-Token") != "" {
		t.Error("token exposed in a header to a cookie client")
	}
	if c := resp.Cookies(); len(c) != 1 || c[0].Value == t0 || !c[0].HttpOnly {
		t.Fatalf("rotated cookie = %+v", c)
	}

	resp, err = client.Get(srv.URL + "/api/me")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("with rotated cookie: status = %d", resp.StatusCode)
	}
}

func TestSessionManagement(t *testing.T) {
	a := newTestAPI(t)
	phone := a.createUser("player", "player-password", false)
	laptop := a.login("player", "player-password")
	other := a.createUser("other", "other-password", false)

	rec := a.do(http.MethodGet, "/api/sessions", laptop, nil)
	expectStatus(t, rec, http.StatusOK)
	list := decode[[]sessionInfo](t, rec)
	if len(list) != 2 || list[0].Current == list[1].Current {
		t.Fatalf("sessions = %+v", list)
	}
	laptopID := list[0].ID
	if !list[0].Current {
		laptopID = list[1].ID
	}

	otherID := decode[[]sessionInfo](t, a.do(http.MethodGet, "/api/sessions", other, nil))[0].ID
	rec = a.do(http.MethodDelete, "/api/sessions/"+otherID.String(), laptop, nil)
	expectStatus(t, rec, http.StatusNotFound)

	expectStatus(t, a.do(http.MethodDelete, "/api/sessions", laptop, nil), http.StatusNoContent)
	expectStatus(t, a.do(http.MethodGet, "/api/me", phone, nil), http.StatusUnauthorized)
	expectStatus(t, a.do(http.MethodGet, "/api/me", laptop, nil), http.StatusOK)
	expectStatus(t, a.do(http.MethodGet, "/api/me", other, nil), http.StatusOK)

	expectStatus(t, a.do(http.MethodDelete, "/api/sessions/"+laptopID.String(), laptop, nil), http.StatusNoContent)
	expectStatus(t, a.do(http.MethodGet, "/api/me", laptop, nil), http.StatusUnauthorized)
}

func TestLastUsedIsTouchedHourly(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	a.exec("UPDATE sessions SET last_used_at = now() - interval '2 hours'")
	expectStatus(t, a.do(http.MethodGet, "/api/me", token, nil), http.StatusOK)

	var lastUsed time.Time
	if err := a.pool.QueryRow(context.Background(), "SELECT last_used_at FROM sessions").Scan(&lastUsed); err != nil {
		t.Fatal(err)
	}
	if time.Since(lastUsed) > time.Minute {
		t.Errorf("last_used_at = %v, want now", lastUsed)
	}
}
