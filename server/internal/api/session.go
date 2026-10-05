package api

import (
	"cmp"
	"context"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/session"
)

const sessionCookie = "session"

// RFC 9110 / RFC 6750: 401 обязан содержать вызов WWW-Authenticate.
func unauthorized(w http.ResponseWriter, code, msg string, invalidToken bool) {
	challenge := `Bearer realm="dataslate"`
	if invalidToken {
		challenge += `, error="invalid_token"`
	}
	w.Header().Set("WWW-Authenticate", challenge)
	writeError(w, http.StatusUnauthorized, code, msg)
}

type principalKey struct{}

// principal — кто делает запрос: пользователь и его сессия.
type principal struct {
	user      dbq.User
	sessionID uuid.UUID
}

func current(r *http.Request) *principal {
	p, _ := r.Context().Value(principalKey{}).(*principal)
	return p
}

func setSessionCookie(w http.ResponseWriter, token string, maxAge time.Duration) {
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookie,
		Value:    token,
		Path:     "/api",
		MaxAge:   int(maxAge.Seconds()),
		HttpOnly: true,
		Secure:   true, // браузеры принимают Secure и на http://localhost
		SameSite: http.SameSiteLaxMode,
	})
}

func clearSessionCookie(w http.ResponseWriter) {
	http.SetCookie(w, &http.Cookie{
		Name: sessionCookie, Path: "/api", MaxAge: -1,
		HttpOnly: true, Secure: true, SameSite: http.SameSiteLaxMode,
	})
}

func sessionToken(r *http.Request) (token string, fromCookie bool) {
	if h := r.Header.Get("Authorization"); h != "" {
		if t, ok := strings.CutPrefix(h, "Bearer "); ok {
			return strings.TrimSpace(t), false
		}
		return "", false
	}
	if c, err := r.Cookie(sessionCookie); err == nil {
		return c.Value, true
	}
	return "", false
}

// requireUser пускает дальше только с действительной сессией. Новый токен после ротации и продлённый срок
// уходят веб-клиенту в cookie, Bearer-клиенту — в X-Session-Token.
func (s *server) requireUser(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token, fromCookie := sessionToken(r)
		if token == "" {
			unauthorized(w, "unauthorized", "authentication required", false)
			return
		}
		now := time.Now()
		a, err := session.Authenticate(r.Context(), s.q, token, now)
		if errors.Is(err, session.ErrInvalid) || errors.Is(err, session.ErrRevoked) {
			if fromCookie {
				clearSessionCookie(w)
			}
			code := "unauthorized"
			if errors.Is(err, session.ErrRevoked) {
				code = "session_revoked"
			}
			unauthorized(w, code, err.Error(), true)
			return
		}
		if err != nil {
			internalError(w, r, err)
			return
		}

		switch {
		case fromCookie && (a.NewToken != "" || a.Extended):
			setSessionCookie(w, cmp.Or(a.NewToken, token), a.ExpiresAt.Sub(now))
		case !fromCookie && a.NewToken != "":
			// Только Bearer-клиентам: веб-токен не должен попадать в JS.
			w.Header().Set("X-Session-Token", a.NewToken)
		}
		ctx := context.WithValue(r.Context(), principalKey{}, &principal{user: a.User, sessionID: a.SessionID})
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func requireAdmin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !current(r).user.IsAdmin {
			writeError(w, http.StatusForbidden, "forbidden", "administrator only")
			return
		}
		next.ServeHTTP(w, r)
	})
}
