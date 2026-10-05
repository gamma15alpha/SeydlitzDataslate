package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

const jsonContentType = "application/json; charset=utf-8"

func TestHealth(t *testing.T) {
	rec := httptest.NewRecorder()
	NewHandler(Config{}).ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/health", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != jsonContentType {
		t.Errorf("Content-Type = %q, want %q", ct, jsonContentType)
	}
	var body healthResponse
	if err := json.NewDecoder(rec.Body).Decode(&body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body.Status != "ok" {
		t.Errorf("status field = %q, want ok", body.Status)
	}
}

func TestUnknownRouteIsNotFound(t *testing.T) {
	rec := httptest.NewRecorder()
	NewHandler(Config{}).ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/nope", nil))

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want 404", rec.Code)
	}
	assertJSONError(t, rec, "not_found")
}

func TestHealthRejectsPost(t *testing.T) {
	rec := httptest.NewRecorder()
	NewHandler(Config{}).ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/health", nil))

	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("status = %d, want 405", rec.Code)
	}
	if allow := rec.Header().Get("Allow"); allow != http.MethodGet {
		t.Errorf("Allow = %q, want GET", allow)
	}
	assertJSONError(t, rec, "method_not_allowed")
}

// assertJSONError проверяет, что ответ — JSON-ошибка с машиночитаемым кодом code.
func assertJSONError(t *testing.T, rec *httptest.ResponseRecorder, code string) {
	t.Helper()
	if ct := rec.Header().Get("Content-Type"); ct != jsonContentType {
		t.Errorf("Content-Type = %q, want %q", ct, jsonContentType)
	}
	var body errorResponse
	if err := json.NewDecoder(rec.Body).Decode(&body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body.Code != code {
		t.Errorf("code = %q, want %q (error: %q)", body.Code, code, body.Error)
	}
	if body.Error == "" {
		t.Error("error message is empty")
	}
}
