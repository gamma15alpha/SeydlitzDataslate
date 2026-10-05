package api

import (
	"cmp"
	"context"
	"errors"
	"log/slog"
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
	// Абсолютный предел от входа (OWASP): дальше продления нет.
	sessionMaxAge      = 90 * 24 * time.Hour
	sessionRotateEvery = 24 * time.Hour
	rotationGrace      = time.Minute
	sessionTouchEvery  = time.Hour
)

// RFC 9110 / RFC 6750: 401 обязан содержать вызов WWW-Authenticate.
func unauthorized(w http.ResponseWriter, code, msg string, invalidToken bool) {
	challenge := `Bearer realm="dataslate"`
	if invalidToken {
		challenge += `, error="invalid_token"`
	}
	w.Header().Set("WWW-Authenticate", challenge)
	writeError(w, http.StatusUnauthorized, code, msg)
}

type sessionKey struct{}

type session struct {
	user dbq.User
	id   uuid.UUID
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
			unauthorized(w, "unauthorized", "authentication required", false)
			return
		}
		ctx := r.Context()
		now := time.Now()
		row, err := s.q.FindSession(ctx, dbq.FindSessionParams{TokenHash: auth.HashSecret(token), CreatedAfter: now.Add(-sessionMaxAge)})
		if errors.Is(err, pgx.ErrNoRows) {
			if fromCookie {
				clearSessionCookie(w)
			}
			unauthorized(w, "unauthorized", "session expired or invalid", true)
			return
		}
		if err != nil {
			internalError(w, r, err)
			return
		}
		if info := logging.Request(ctx); info != nil {
			info.UserID = row.User.ID.String()
		}

		newToken, err := s.rotate(ctx, row, token, now)
		if errors.Is(err, errTokenReuse) {
			slog.WarnContext(ctx, "session token reuse, session revoked", "session_id", row.SessionID)
			if fromCookie {
				clearSessionCookie(w)
			}
			unauthorized(w, "session_revoked", "session revoked: token reuse detected", true)
			return
		}
		if err != nil {
			internalError(w, r, err)
			return
		}

		expires := now.Add(sessionTTL)
		if limit := row.SessionCreatedAt.Add(sessionMaxAge); expires.After(limit) {
			expires = limit
		}
		extend := row.SessionExpiresAt.Sub(now) < sessionRefresh && expires.After(row.SessionExpiresAt)
		if !extend {
			expires = row.SessionExpiresAt
		}
		if extend || now.Sub(row.LastUsedAt) >= sessionTouchEvery {
			if err := s.q.TouchSession(ctx, dbq.TouchSessionParams{ID: row.SessionID, ExpiresAt: expires}); err != nil {
				internalError(w, r, err)
				return
			}
		}

		switch {
		case fromCookie && (newToken != "" || extend):
			setSessionCookie(w, cmp.Or(newToken, token), expires.Sub(now))
		case !fromCookie && newToken != "":
			// Только Bearer-клиентам: веб-токен не должен попадать в JS.
			w.Header().Set("X-Session-Token", newToken)
		}
		ctx = context.WithValue(ctx, sessionKey{}, &session{user: row.User, id: row.SessionID})
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

var errTokenReuse = errors.New("session token reuse")

// rotate раз в сутки выдаёт новый токен. Старый принимается, пока новый не пришёл ни разу
// (ответ мог потеряться), и ещё rotationGrace после — для параллельных запросов.
// Старый токен позже — его использует кто-то ещё: сессия отзывается.
func (s *server) rotate(ctx context.Context, row dbq.FindSessionRow, token string, now time.Time) (string, error) {
	newToken, newHash := auth.NewSecret()
	hash := auth.HashSecret(token)
	if row.IsCurrent {
		if row.ConfirmedAt == nil {
			if err := s.q.ConfirmSession(ctx, row.SessionID); err != nil {
				return "", err
			}
		}
		if now.Sub(row.RotatedAt) < sessionRotateEvery {
			return "", nil
		}
		n, err := s.q.RotateSession(ctx, dbq.RotateSessionParams{ID: row.SessionID, NewHash: newHash, CurrentHash: hash})
		return issued(newToken, n, err)
	}
	switch {
	case row.ConfirmedAt == nil:
		n, err := s.q.ReissueSession(ctx, dbq.ReissueSessionParams{
			ID: row.SessionID, NewHash: newHash, PrevHash: hash, ReissueBefore: now.Add(-rotationGrace),
		})
		return issued(newToken, n, err)
	case now.Sub(*row.ConfirmedAt) < rotationGrace:
		return "", nil
	default:
		if err := s.q.DeleteSession(ctx, row.SessionID); err != nil {
			return "", err
		}
		return "", errTokenReuse
	}
}

// issued — токен выдан, только если обновление прошло (иначе его уже выдал параллельный запрос).
func issued(token string, rows int64, err error) (string, error) {
	if err != nil || rows == 0 {
		return "", err
	}
	return token, nil
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
