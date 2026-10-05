package api

import (
	"errors"
	"net/http"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

type userResponse struct {
	ID          uuid.UUID `json:"id"`
	Login       string    `json:"login"`
	DisplayName string    `json:"displayName"`
	IsAdmin     bool      `json:"isAdmin"`
	CreatedAt   time.Time `json:"createdAt"`
}

func toUserResponse(u dbq.User) userResponse {
	return userResponse{ID: u.ID, Login: u.Login, DisplayName: u.DisplayName, IsAdmin: u.IsAdmin, CreatedAt: u.CreatedAt}
}

// Token — для Android; веб использует cookie.
type sessionResponse struct {
	Token string       `json:"token"`
	User  userResponse `json:"user"`
}

type registerRequest struct {
	Invite      string `json:"invite"`
	Login       string `json:"login"`
	Password    string `json:"password"`
	DisplayName string `json:"displayName"`
}

func (s *server) register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	ipKey := ipKey(r)
	if limited(w, func() (bool, time.Duration) { return s.ipLimiter.Blocked(ipKey) }) {
		return
	}

	req.Login = strings.TrimSpace(req.Login)
	req.DisplayName = strings.TrimSpace(req.DisplayName)
	if req.DisplayName == "" {
		req.DisplayName = req.Login
	}
	if msg := auth.ValidateLogin(req.Login); msg != "" {
		writeError(w, http.StatusBadRequest, "invalid_login", msg)
		return
	}
	if msg := auth.ValidatePassword(req.Password); msg != "" {
		writeError(w, http.StatusBadRequest, "invalid_password", msg)
		return
	}
	if utf8.RuneCountInString(req.DisplayName) > 64 {
		writeError(w, http.StatusBadRequest, "invalid_display_name", "display name must be at most 64 characters long")
		return
	}
	passwordHash := auth.HashPassword(req.Password) // до транзакции: argon2 медленный

	ctx := r.Context()
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		internalError(w, r, err)
		return
	}
	defer tx.Rollback(ctx)
	q := s.q.WithTx(tx)

	inviteID, err := q.ClaimInvite(ctx, auth.HashSecret(req.Invite))
	if errors.Is(err, pgx.ErrNoRows) {
		s.ipLimiter.Fail(ipKey)
		writeError(w, http.StatusBadRequest, "invalid_invite", "invite is invalid, expired or already used")
		return
	}
	if err != nil {
		internalError(w, r, err)
		return
	}
	user, err := q.CreateUser(ctx, dbq.CreateUserParams{
		Login: req.Login, DisplayName: req.DisplayName, PasswordHash: passwordHash,
	})
	if db.IsUniqueViolation(err) {
		writeError(w, http.StatusConflict, "login_taken", "login is already taken")
		return // откат вернёт инвайт
	}
	if err != nil {
		internalError(w, r, err)
		return
	}
	if err := q.SetInviteUser(ctx, dbq.SetInviteUserParams{ID: inviteID, UsedBy: &user.ID}); err != nil {
		internalError(w, r, err)
		return
	}
	token, err := s.createSession(ctx, q, r, user.ID)
	if err != nil {
		internalError(w, r, err)
		return
	}
	if err := tx.Commit(ctx); err != nil {
		internalError(w, r, err)
		return
	}
	setSessionCookie(w, token, sessionTTL)
	writeJSON(w, http.StatusCreated, sessionResponse{Token: token, User: toUserResponse(user)})
}

type loginRequest struct {
	Login    string `json:"login"`
	Password string `json:"password"`
}

func (s *server) login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	ipKey := ipKey(r)
	loginKey := strings.ToLower(strings.TrimSpace(req.Login))
	if limited(w,
		func() (bool, time.Duration) { return s.ipLimiter.Blocked(ipKey) },
		func() (bool, time.Duration) { return s.loginLimiter.Blocked(loginKey) },
	) {
		return
	}
	fail := func() {
		s.ipLimiter.Fail(ipKey)
		s.loginLimiter.Fail(loginKey)
		unauthorized(w, "invalid_credentials", "invalid login or password", false)
	}

	ctx := r.Context()
	user, err := s.q.GetUserByLogin(ctx, loginKey)
	if errors.Is(err, pgx.ErrNoRows) {
		auth.VerifyDummy(req.Password)
		fail()
		return
	}
	if err != nil {
		internalError(w, r, err)
		return
	}
	ok, err := auth.VerifyPassword(req.Password, user.PasswordHash)
	if err != nil {
		internalError(w, r, err)
		return
	}
	if !ok {
		fail()
		return
	}
	s.loginLimiter.Reset(loginKey)

	token, err := s.createSession(ctx, s.q, r, user.ID)
	if err != nil {
		internalError(w, r, err)
		return
	}
	setSessionCookie(w, token, sessionTTL)
	writeJSON(w, http.StatusOK, sessionResponse{Token: token, User: toUserResponse(user)})
}

func (s *server) logout(w http.ResponseWriter, r *http.Request) {
	if err := s.q.DeleteSession(r.Context(), currentSession(r).id); err != nil {
		internalError(w, r, err)
		return
	}
	clearSessionCookie(w)
	w.WriteHeader(http.StatusNoContent)
}

func (s *server) me(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, toUserResponse(currentSession(r).user))
}
