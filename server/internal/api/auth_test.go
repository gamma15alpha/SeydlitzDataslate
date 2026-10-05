package api

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbtest"
)

type testAPI struct {
	t *testing.T
	h http.Handler
	q *dbq.Queries
}

func newTestAPI(t *testing.T) *testAPI {
	t.Helper()
	return newTestAPIWith(t, Config{})
}

func newTestAPIWith(t *testing.T, cfg Config) *testAPI {
	t.Helper()
	pool := dbtest.New(t)
	cfg.Pool = pool
	return &testAPI{t: t, h: NewHandler(cfg), q: dbq.New(pool)}
}

func (a *testAPI) do(method, path, token string, body any) *httptest.ResponseRecorder {
	a.t.Helper()
	var buf bytes.Buffer
	if body != nil {
		if err := json.NewEncoder(&buf).Encode(body); err != nil {
			a.t.Fatal(err)
		}
	}
	req := httptest.NewRequest(method, path, &buf)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	a.h.ServeHTTP(rec, req)
	return rec
}

func decode[T any](t *testing.T, rec *httptest.ResponseRecorder) T {
	t.Helper()
	var v T
	if err := json.NewDecoder(rec.Body).Decode(&v); err != nil {
		t.Fatalf("decode %T: %v (body: %s)", v, err, rec.Body.String())
	}
	return v
}

func expectStatus(t *testing.T, rec *httptest.ResponseRecorder, want int) {
	t.Helper()
	if rec.Code != want {
		t.Fatalf("status = %d, want %d (body: %s)", rec.Code, want, rec.Body.String())
	}
}

func (a *testAPI) createUser(login, password string, admin bool) string {
	a.t.Helper()
	_, err := a.q.CreateUser(context.Background(), dbq.CreateUserParams{
		Login: login, DisplayName: login, PasswordHash: auth.HashPassword(password), IsAdmin: admin,
	})
	if err != nil {
		a.t.Fatalf("create user: %v", err)
	}
	return a.login(login, password)
}

func (a *testAPI) login(login, password string) string {
	a.t.Helper()
	rec := a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: login, Password: password})
	expectStatus(a.t, rec, http.StatusOK)
	return decode[sessionResponse](a.t, rec).Token
}

func (a *testAPI) createInvite(adminToken string) string {
	a.t.Helper()
	rec := a.do(http.MethodPost, "/api/invites", adminToken, nil)
	expectStatus(a.t, rec, http.StatusCreated)
	return decode[inviteResponse](a.t, rec).Code
}

func TestRegistrationByInvite(t *testing.T) {
	a := newTestAPI(t)
	admin := a.createUser("admin", "admin-password", true)
	invite := a.createInvite(admin)

	rec := a.do(http.MethodPost, "/api/auth/register", "", registerRequest{
		Invite: "  " + strings.ToLower(invite) + " ", Login: "Player", Password: "player-password",
	})
	expectStatus(t, rec, http.StatusCreated)
	reg := decode[sessionResponse](t, rec)
	if reg.User.Login != "Player" || reg.User.DisplayName != "Player" || reg.User.IsAdmin {
		t.Errorf("registered user = %+v", reg.User)
	}

	rec = a.do(http.MethodGet, "/api/me", reg.Token, nil)
	expectStatus(t, rec, http.StatusOK)
	if me := decode[userResponse](t, rec); me.ID != reg.User.ID {
		t.Errorf("/api/me id = %s, want %s", me.ID, reg.User.ID)
	}

	t.Run("invite is single-use", func(t *testing.T) {
		rec := a.do(http.MethodPost, "/api/auth/register", "", registerRequest{
			Invite: invite, Login: "another", Password: "another-password",
		})
		expectStatus(t, rec, http.StatusBadRequest)
		assertJSONError(t, rec, "invalid_invite")
	})

	t.Run("invite list shows who used it", func(t *testing.T) {
		rec := a.do(http.MethodGet, "/api/invites", admin, nil)
		expectStatus(t, rec, http.StatusOK)
		list := decode[[]inviteResponse](t, rec)
		if len(list) != 1 || list[0].UsedBy == nil || *list[0].UsedBy != "Player" || list[0].Code != "" {
			t.Errorf("invites = %+v", list)
		}
	})

	t.Run("login is case-insensitive", func(t *testing.T) {
		a.login("pLaYeR", "player-password")
	})
}

func TestRegisterTakenLoginKeepsInvite(t *testing.T) {
	a := newTestAPI(t)
	admin := a.createUser("admin", "admin-password", true)
	invite := a.createInvite(admin)

	rec := a.do(http.MethodPost, "/api/auth/register", "", registerRequest{
		Invite: invite, Login: "ADMIN", Password: "player-password",
	})
	expectStatus(t, rec, http.StatusConflict)
	assertJSONError(t, rec, "login_taken")

	// Инвайт не сгорел.
	rec = a.do(http.MethodPost, "/api/auth/register", "", registerRequest{
		Invite: invite, Login: "player", Password: "player-password",
	})
	expectStatus(t, rec, http.StatusCreated)
}

func TestRegisterValidation(t *testing.T) {
	a := newTestAPI(t)
	admin := a.createUser("admin", "admin-password", true)
	invite := a.createInvite(admin)

	cases := []struct {
		name string
		req  registerRequest
		code string
	}{
		{"short login", registerRequest{Invite: invite, Login: "ab", Password: "player-password"}, "invalid_login"},
		{"bad login chars", registerRequest{Invite: invite, Login: "игрок", Password: "player-password"}, "invalid_login"},
		{"short password", registerRequest{Invite: invite, Login: "player", Password: "short"}, "invalid_password"},
		{"unknown invite", registerRequest{Invite: "NOPE", Login: "player", Password: "player-password"}, "invalid_invite"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			rec := a.do(http.MethodPost, "/api/auth/register", "", c.req)
			expectStatus(t, rec, http.StatusBadRequest)
			assertJSONError(t, rec, c.code)
		})
	}
}

func TestExpiredInviteRejected(t *testing.T) {
	a := newTestAPI(t)
	a.createUser("admin", "admin-password", true)
	adminUser, err := a.q.GetUserByLogin(context.Background(), "admin")
	if err != nil {
		t.Fatal(err)
	}
	code, hash := auth.NewSecret()
	_, err = a.q.CreateInvite(context.Background(), dbq.CreateInviteParams{
		CodeHash: hash, CreatedBy: adminUser.ID, ExpiresAt: time.Now().Add(-time.Minute),
	})
	if err != nil {
		t.Fatal(err)
	}

	rec := a.do(http.MethodPost, "/api/auth/register", "", registerRequest{Invite: code, Login: "player", Password: "player-password"})
	expectStatus(t, rec, http.StatusBadRequest)
	assertJSONError(t, rec, "invalid_invite")
}

func TestLoginAndLogout(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)

	rec := a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: "player", Password: "wrong-password"})
	expectStatus(t, rec, http.StatusUnauthorized)
	assertJSONError(t, rec, "invalid_credentials")

	rec = a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: "nobody", Password: "whatever-password"})
	expectStatus(t, rec, http.StatusUnauthorized)
	assertJSONError(t, rec, "invalid_credentials")

	expectStatus(t, a.do(http.MethodPost, "/api/auth/logout", token, nil), http.StatusNoContent)
	rec = a.do(http.MethodGet, "/api/me", token, nil)
	expectStatus(t, rec, http.StatusUnauthorized)
	assertJSONError(t, rec, "unauthorized")
}

func TestLoginRateLimit(t *testing.T) {
	a := newTestAPI(t)
	a.createUser("player", "player-password", false)

	for range 10 {
		expectStatus(t, a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: "player", Password: "wrong-password"}),
			http.StatusUnauthorized)
	}
	rec := a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: "player", Password: "player-password"})
	expectStatus(t, rec, http.StatusTooManyRequests)
	assertJSONError(t, rec, "too_many_attempts")
	if rec.Header().Get("Retry-After") == "" {
		t.Error("Retry-After header is missing")
	}
}

func TestInvitesRequireAdmin(t *testing.T) {
	a := newTestAPI(t)
	player := a.createUser("player", "player-password", false)

	rec := a.do(http.MethodPost, "/api/invites", player, nil)
	expectStatus(t, rec, http.StatusForbidden)
	assertJSONError(t, rec, "forbidden")

	rec = a.do(http.MethodGet, "/api/invites", "", nil)
	expectStatus(t, rec, http.StatusUnauthorized)
	assertJSONError(t, rec, "unauthorized")
}

func TestCreateAndDeleteInvite(t *testing.T) {
	a := newTestAPI(t)
	admin := a.createUser("admin", "admin-password", true)

	rec := a.do(http.MethodPost, "/api/invites", admin, createInviteRequest{ExpiresInDays: 91})
	expectStatus(t, rec, http.StatusBadRequest)
	assertJSONError(t, rec, "invalid_expiry")

	rec = a.do(http.MethodPost, "/api/invites", admin, createInviteRequest{ExpiresInDays: 30})
	expectStatus(t, rec, http.StatusCreated)
	inv := decode[inviteResponse](t, rec)
	if days := time.Until(inv.ExpiresAt).Hours() / 24; days < 29.9 || days > 30 {
		t.Errorf("invite expires in %.2f days, want 30", days)
	}

	expectStatus(t, a.do(http.MethodDelete, "/api/invites/"+inv.ID.String(), admin, nil), http.StatusNoContent)
	rec = a.do(http.MethodDelete, "/api/invites/"+inv.ID.String(), admin, nil)
	expectStatus(t, rec, http.StatusNotFound)
	assertJSONError(t, rec, "not_found")

	rec = a.do(http.MethodPost, "/api/auth/register", "", registerRequest{Invite: inv.Code, Login: "player", Password: "player-password"})
	expectStatus(t, rec, http.StatusBadRequest)
}

func TestRequiresJSONContentType(t *testing.T) {
	a := newTestAPI(t)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/login", strings.NewReader("login=a&password=b"))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	a.h.ServeHTTP(rec, req)
	expectStatus(t, rec, http.StatusUnsupportedMediaType)
	assertJSONError(t, rec, "unsupported_media_type")
}

func TestRejectsBadBodies(t *testing.T) {
	a := newTestAPI(t)
	post := func(body []byte) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		rec := httptest.NewRecorder()
		a.h.ServeHTTP(rec, req)
		return rec
	}

	// cp1251 — так шлёт curl в консоли Windows.
	rec := post([]byte("{\"login\":\"\xc8\xed\xea\xe2\",\"password\":\"x\"}"))
	expectStatus(t, rec, http.StatusBadRequest)
	assertJSONError(t, rec, "bad_request")

	rec = post(append([]byte(`{"login":"`), bytes.Repeat([]byte("a"), maxBodyBytes)...))
	expectStatus(t, rec, http.StatusRequestEntityTooLarge)
	assertJSONError(t, rec, "too_large")
}

func TestCookieSession(t *testing.T) {
	a := newTestAPI(t)
	a.createUser("player", "player-password", false)

	srv := httptest.NewTLSServer(a.h) // Secure-cookie cookiejar шлёт только по https
	t.Cleanup(srv.Close)
	client := srv.Client()
	client.Jar, _ = cookiejar.New(nil)

	post := func(path, body string) *http.Response {
		t.Helper()
		resp, err := client.Post(srv.URL+path, "application/json", strings.NewReader(body))
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		return resp
	}
	get := func(path string) int {
		t.Helper()
		resp, err := client.Get(srv.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		return resp.StatusCode
	}

	resp := post("/api/auth/login", `{"login":"player","password":"player-password"}`)
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("login status = %d", resp.StatusCode)
	}
	cookies := resp.Cookies()
	if len(cookies) != 1 || !cookies[0].HttpOnly || !cookies[0].Secure || cookies[0].SameSite != http.SameSiteLaxMode {
		t.Fatalf("session cookie = %+v", cookies)
	}

	if code := get("/api/me"); code != http.StatusOK {
		t.Fatalf("/api/me with cookie: status = %d", code)
	}
	post("/api/auth/logout", "")
	if code := get("/api/me"); code != http.StatusUnauthorized {
		t.Fatalf("/api/me after logout: status = %d, want 401", code)
	}
}

func TestUserRateLimit(t *testing.T) {
	a := newTestAPIWith(t, Config{UserRequestsPerMinute: 2})
	token := a.createUser("player", "player-password", false)

	expectStatus(t, a.do(http.MethodGet, "/api/me", token, nil), http.StatusOK)
	expectStatus(t, a.do(http.MethodGet, "/api/me", token, nil), http.StatusOK)
	rec := a.do(http.MethodGet, "/api/me", token, nil)
	expectStatus(t, rec, http.StatusTooManyRequests)
	assertJSONError(t, rec, "rate_limited")

	other := a.createUser("other", "other-password", false)
	expectStatus(t, a.do(http.MethodGet, "/api/me", other, nil), http.StatusOK)
}

func TestRequestLogsAreCorrelated(t *testing.T) {
	logs := captureLogs(t) // до открытия базы: от уровня зависит SQL-трассировка
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	logs.Reset()

	rec := a.do(http.MethodGet, "/api/me", token, nil)
	expectStatus(t, rec, http.StatusOK)
	id := rec.Header().Get("X-Request-ID")
	me := decode[userResponse](t, rec)

	var sawQuery, sawAccess bool
	for _, r := range logRecords(t, logs) {
		if r["request_id"] != id {
			t.Errorf("record without this request's id: %v", r)
			continue
		}
		switch r["msg"] {
		case "db query":
			if r["query"] == "GetSessionUser" {
				sawQuery = true
			}
			if _, leaked := r["args"]; leaked {
				t.Errorf("query args are logged: %v", r)
			}
		case "request":
			sawAccess = r["user_id"] == me.ID.String() && r["route"] == "/api/me"
		}
	}
	if !sawQuery || !sawAccess {
		t.Errorf("query logged: %v, access log with user_id: %v\n%s", sawQuery, sawAccess, logs)
	}
}
