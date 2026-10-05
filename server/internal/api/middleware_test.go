package api

import (
	"bytes"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/logging"
)

func captureLogs(t *testing.T) *bytes.Buffer {
	t.Helper()
	var buf bytes.Buffer
	logger, err := logging.New(&buf, "json", "debug")
	if err != nil {
		t.Fatal(err)
	}
	prev := slog.Default()
	slog.SetDefault(logger)
	t.Cleanup(func() { slog.SetDefault(prev) })
	return &buf
}

func logRecords(t *testing.T, buf *bytes.Buffer) []map[string]any {
	t.Helper()
	var records []map[string]any
	for line := range bytes.Lines(buf.Bytes()) {
		var rec map[string]any
		if err := json.Unmarshal(line, &rec); err != nil {
			t.Fatalf("log line %q: %v", line, err)
		}
		records = append(records, rec)
	}
	return records
}

func accessLog(t *testing.T, buf *bytes.Buffer) []map[string]any {
	t.Helper()
	var out []map[string]any
	for _, rec := range logRecords(t, buf) {
		if rec["msg"] == "request" {
			out = append(out, rec)
		}
	}
	return out
}

func serve(h http.Handler, req *http.Request) *httptest.ResponseRecorder {
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func TestAccessLogAndRequestID(t *testing.T) {
	logs := captureLogs(t)
	rec := serve(NewHandler(Config{}), httptest.NewRequest(http.MethodGet, "/api/health", nil))

	id := rec.Header().Get("X-Request-ID")
	if len(id) != 16 {
		t.Fatalf("X-Request-ID = %q, want 16 characters", id)
	}
	entries := accessLog(t, logs)
	if len(entries) != 1 {
		t.Fatalf("got %d access log records, want 1", len(entries))
	}
	e := entries[0]
	want := map[string]any{
		"level": "INFO", "request_id": id, "method": "GET", "path": "/api/health",
		"route": "/api/health", "status": float64(200), "ip": "192.0.2.1",
	}
	for k, v := range want {
		if e[k] != v {
			t.Errorf("%s = %v, want %v", k, e[k], v)
		}
	}
	if _, ok := e["duration_ms"].(float64); !ok {
		t.Errorf("duration_ms missing: %v", e)
	}
}

func TestRequestIDFromProxy(t *testing.T) {
	cases := []struct {
		name       string
		trustProxy bool
		incoming   string
		keep       bool
	}{
		{"trusted proxy", true, "nginx-0f3a.9_b", true},
		{"untrusted client", false, "client-chosen", false},
		{"invalid characters", true, "bad id\n", false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			captureLogs(t)
			req := httptest.NewRequest(http.MethodGet, "/api/health", nil)
			req.Header.Set("X-Request-ID", c.incoming)
			got := serve(NewHandler(Config{TrustProxy: c.trustProxy}), req).Header().Get("X-Request-ID")
			if (got == c.incoming) != c.keep || got == "" {
				t.Errorf("X-Request-ID = %q (incoming %q, keep %v)", got, c.incoming, c.keep)
			}
		})
	}
}

func TestPanicBecomes500(t *testing.T) {
	logs := captureLogs(t)
	h := requestLogger(false)(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { panic("boom") }))
	rec := serve(h, httptest.NewRequest(http.MethodGet, "/api/x", nil))

	expectStatus(t, rec, http.StatusInternalServerError)
	assertJSONError(t, rec, "internal")
	var sawPanic bool
	for _, r := range logRecords(t, logs) {
		if r["msg"] == "panic" && r["panic"] == "boom" && r["stack"] != "" {
			sawPanic = true
		}
	}
	if !sawPanic {
		t.Error("panic is not logged")
	}
	if e := accessLog(t, logs); len(e) != 1 || e[0]["status"] != float64(500) || e[0]["level"] != "ERROR" {
		t.Errorf("access log = %v", e)
	}
}

func TestIPRateLimit(t *testing.T) {
	captureLogs(t)
	h := NewHandler(Config{IPRequestsPerMinute: 3})
	from := func(addr string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodGet, "/api/health", nil)
		req.RemoteAddr = addr
		return serve(h, req)
	}

	for range 3 {
		expectStatus(t, from("192.0.2.1:1000"), http.StatusOK)
	}
	rec := from("192.0.2.1:2000") // другой порт — тот же клиент
	expectStatus(t, rec, http.StatusTooManyRequests)
	assertJSONError(t, rec, "rate_limited")
	if rec.Header().Get("Retry-After") == "" {
		t.Error("Retry-After header is missing")
	}
	expectStatus(t, from("198.51.100.7:1000"), http.StatusOK)

	// Одна сеть /64 — общий лимит.
	for i := range 3 {
		expectStatus(t, from("[2001:db8::"+string(rune('1'+i))+"]:1000"), http.StatusOK)
	}
	expectStatus(t, from("[2001:db8::ffff]:1000"), http.StatusTooManyRequests)
	expectStatus(t, from("[2001:db8:0:1::1]:1000"), http.StatusOK)
}
