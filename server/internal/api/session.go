package api

import (
	"context"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/logging"
)

const (
	sessionCookie = "session"
	sessionTTL    = 30 * 24 * time.Hour
	// Продлеваем, только когда осталось меньше, — не пишем в базу на каждый запрос.
	sessionRefresh = 15 * 24 * time.Hour
)

type sessionKey struct{}

type session struct {
	user      dbq.User
	tokenHash []byte
}

func currentSession(r *http.Request) *session {
	s, _ := r.Context().Value(sessionKey{}).(*session)
	return s
}

func (s *server) createSession(ctx context.Context, q *dbq.Queries, r *http.Request, userID uuid.UUID) (string, error) {
	token, hash := auth.NewSecret()
	ua := r.UserAgent()
	if len(ua) > 256 {
		ua = ua[:256]
	}
	err := q.CreateSession(ctx, dbq.CreateSessionParams{
		TokenHash: hash,
		UserID:    userID,
		ExpiresAt: time.Now().Add(sessionTTL),
		UserAgent: ua,
	})
	return token, err
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

func (s *server) requireUser(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token, fromCookie := sessionToken(r)
		if token == "" {
			writeError(w, http.StatusUnauthorized, "unauthorized", "authentication required")
			return
		}
		hash := auth.HashSecret(token)
		row, err := s.q.GetSessionUser(r.Context(), hash)
		if errors.Is(err, pgx.ErrNoRows) {
			if fromCookie {
				clearSessionCookie(w)
			}
			writeError(w, http.StatusUnauthorized, "unauthorized", "session expired or invalid")
			return
		}
		if err != nil {
			internalError(w, r, err)
			return
		}
		if time.Until(row.SessionExpiresAt) < sessionRefresh {
			err := s.q.ExtendSession(r.Context(), dbq.ExtendSessionParams{TokenHash: hash, ExpiresAt: time.Now().Add(sessionTTL)})
			if err != nil {
				internalError(w, r, err)
				return
			}
			if fromCookie {
				setSessionCookie(w, token, sessionTTL)
			}
		}
		if info := logging.Request(r.Context()); info != nil {
			info.UserID = row.User.ID.String()
		}
		ctx := context.WithValue(r.Context(), sessionKey{}, &session{user: row.User, tokenHash: hash})
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func requireAdmin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !currentSession(r).user.IsAdmin {
			writeError(w, http.StatusForbidden, "forbidden", "administrator only")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func limited(w http.ResponseWriter, checks ...func() (bool, time.Duration)) bool {
	for _, check := range checks {
		if blocked, retry := check(); blocked {
			w.Header().Set("Retry-After", strconv.Itoa(int(retry.Seconds())+1))
			writeError(w, http.StatusTooManyRequests, "too_many_attempts", "too many failed attempts, try again later")
			return true
		}
	}
	return false
}
