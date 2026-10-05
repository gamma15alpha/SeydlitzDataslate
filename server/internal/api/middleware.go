package api

import (
	"crypto/rand"
	"log/slog"
	"net"
	"net/http"
	"runtime/debug"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/httprate"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/logging"
)

// requestLogger присваивает запросу ID (X-Request-ID), пишет access-лог и превращает панику в 500.
// ID извне принимается только при trustProxy.
func requestLogger(trustProxy bool) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			id := ""
			if trustProxy {
				id = validRequestID(r.Header.Get("X-Request-ID"))
			}
			if id == "" {
				id = rand.Text()[:16]
			}
			info := &logging.RequestInfo{ID: id}
			ctx := logging.WithRequest(r.Context(), info)
			w.Header().Set("X-Request-ID", id)
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)

			defer func() {
				if p := recover(); p != nil {
					if p == http.ErrAbortHandler {
						panic(p) // штатный обрыв ответа
					}
					slog.ErrorContext(ctx, "panic", "panic", p, "stack", string(debug.Stack()))
					if ww.Status() == 0 {
						writeError(ww, http.StatusInternalServerError, "internal", "Internal Server Error")
					}
				}
				status := ww.Status()
				if status == 0 {
					status = http.StatusOK
				}
				level := slog.LevelInfo
				if status >= 500 {
					level = slog.LevelError
				}
				slog.LogAttrs(ctx, level, "request",
					slog.String("method", r.Method),
					slog.String("path", r.URL.Path), // без query: там могут быть секреты
					slog.String("route", routePattern(r)),
					slog.Int("status", status),
					slog.Int("bytes", ww.BytesWritten()),
					slog.Float64("duration_ms", float64(time.Since(start).Microseconds())/1000),
					slog.String("ip", clientIP(r)),
				)
			}()
			next.ServeHTTP(ww, r.WithContext(ctx))
		})
	}
}

func routePattern(r *http.Request) string {
	if rctx := chi.RouteContext(r.Context()); rctx != nil {
		return rctx.RoutePattern()
	}
	return ""
}

func validRequestID(id string) string {
	if len(id) == 0 || len(id) > 64 {
		return ""
	}
	for _, c := range id {
		if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_' || c == '.') {
			return ""
		}
	}
	return id
}

func rateLimit(perMinute int, key func(*http.Request) string) func(http.Handler) http.Handler {
	return httprate.Limit(perMinute, time.Minute,
		httprate.WithKeyFuncs(func(r *http.Request) (string, error) { return key(r), nil }),
		httprate.WithLimitHandler(func(w http.ResponseWriter, r *http.Request) {
			slog.WarnContext(r.Context(), "rate limited", "ip", clientIP(r))
			writeError(w, http.StatusTooManyRequests, "rate_limited", "too many requests, slow down")
		}),
		httprate.WithErrorHandler(func(w http.ResponseWriter, r *http.Request, err error) {
			internalError(w, r, err)
		}),
	)
}

func clientIP(r *http.Request) string {
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		return host
	}
	return r.RemoteAddr
}

// IPv6 — по сети /64, иначе лимит обходится сменой адреса.
func ipKey(r *http.Request) string {
	return httprate.CanonicalizeIP(clientIP(r))
}

func userKey(r *http.Request) string {
	return current(r).user.ID.String()
}
