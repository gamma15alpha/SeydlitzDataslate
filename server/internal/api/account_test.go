package api

import (
	"net/http"
	"testing"
)

func TestChangePassword(t *testing.T) {
	a := newTestAPI(t)
	current := a.createUser("player", "old-password", false)
	other := a.login("player", "old-password")

	rec := a.do(http.MethodPut, "/api/me/password", current, changePasswordRequest{CurrentPassword: "wrong-password", NewPassword: "new-password"})
	expectStatus(t, rec, http.StatusForbidden)
	assertJSONError(t, rec, "wrong_password")

	rec = a.do(http.MethodPut, "/api/me/password", current, changePasswordRequest{CurrentPassword: "old-password", NewPassword: "short"})
	expectStatus(t, rec, http.StatusBadRequest)
	assertJSONError(t, rec, "invalid_password")

	expectStatus(t, a.do(http.MethodPut, "/api/me/password", current,
		changePasswordRequest{CurrentPassword: "old-password", NewPassword: "new-password"}), http.StatusNoContent)

	// Текущая сессия живёт, остальные завершены.
	expectStatus(t, a.do(http.MethodGet, "/api/me", current, nil), http.StatusOK)
	expectStatus(t, a.do(http.MethodGet, "/api/me", other, nil), http.StatusUnauthorized)

	rec = a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: "player", Password: "old-password"})
	expectStatus(t, rec, http.StatusUnauthorized)
	a.login("player", "new-password")
}

func TestChangeLogin(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	other := a.login("player", "player-password")
	a.createUser("Taken", "taken-password", false)

	rec := a.do(http.MethodPut, "/api/me/login", token, changeLoginRequest{Login: "no spaces", Password: "player-password"})
	expectStatus(t, rec, http.StatusBadRequest)
	assertJSONError(t, rec, "invalid_login")

	rec = a.do(http.MethodPut, "/api/me/login", token, changeLoginRequest{Login: "renamed", Password: "wrong-password"})
	expectStatus(t, rec, http.StatusForbidden)
	assertJSONError(t, rec, "wrong_password")

	rec = a.do(http.MethodPut, "/api/me/login", token, changeLoginRequest{Login: "taken", Password: "player-password"})
	expectStatus(t, rec, http.StatusConflict)
	assertJSONError(t, rec, "login_taken")

	rec = a.do(http.MethodPut, "/api/me/login", token, changeLoginRequest{Login: " Renamed ", Password: "player-password"})
	expectStatus(t, rec, http.StatusOK)
	if got := decode[userResponse](t, rec).Login; got != "Renamed" {
		t.Errorf("login = %q, want Renamed", got)
	}

	// Свой логин можно поменять и только регистром.
	rec = a.do(http.MethodPut, "/api/me/login", token, changeLoginRequest{Login: "renamed", Password: "player-password"})
	expectStatus(t, rec, http.StatusOK)
	if got := decode[userResponse](t, rec).Login; got != "renamed" {
		t.Errorf("login = %q, want renamed", got)
	}

	// Сессии не завершаются; входят уже по новому логину.
	expectStatus(t, a.do(http.MethodGet, "/api/me", other, nil), http.StatusOK)
	expectStatus(t, a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: "player", Password: "player-password"}), http.StatusUnauthorized)
	a.login("RENAMED", "player-password")
}

// Подбор пароля через сессию упирается в тот же лимит, что и вход: 10 неудач на логин.
func TestConfirmPasswordIsRateLimited(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)

	for range 10 {
		rec := a.do(http.MethodPut, "/api/me/password", token, changePasswordRequest{CurrentPassword: "wrong-password", NewPassword: "new-password"})
		expectStatus(t, rec, http.StatusForbidden)
	}
	rec := a.do(http.MethodPut, "/api/me/login", token, changeLoginRequest{Login: "renamed", Password: "player-password"})
	expectStatus(t, rec, http.StatusTooManyRequests)
	assertJSONError(t, rec, "too_many_attempts")
	if rec.Header().Get("Retry-After") == "" {
		t.Error("no Retry-After")
	}
	expectStatus(t, a.do(http.MethodPost, "/api/auth/login", "", loginRequest{Login: "player", Password: "player-password"}), http.StatusTooManyRequests)
}
