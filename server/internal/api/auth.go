package api

import (
	"encoding/hex"
	"net/http"
	"time"

	"github.com/google/uuid"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/account"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/session"
)

type userResponse struct {
	ID          uuid.UUID `json:"id"`
	Login       string    `json:"login"`
	DisplayName string    `json:"displayName"`
	IsAdmin     bool      `json:"isAdmin"`
	CreatedAt   time.Time `json:"createdAt"`
	Avatar      *string   `json:"avatar"` // SHA-256 в hex: GET /api/blobs/{sha256}
}

func toUserResponse(u dbq.User) userResponse {
	r := userResponse{ID: u.ID, Login: u.Login, DisplayName: u.DisplayName, IsAdmin: u.IsAdmin, CreatedAt: u.CreatedAt}
	if u.AvatarSha256 != nil {
		avatar := hex.EncodeToString(u.AvatarSha256)
		r.Avatar = &avatar
	}
	return r
}

// Token — для Android; веб использует cookie.
type sessionResponse struct {
	Token string       `json:"token"`
	User  userResponse `json:"user"`
}

func clientOf(r *http.Request) account.Client {
	return account.Client{IP: ipKey(r), UserAgent: r.UserAgent()}
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
	user, token, err := s.accounts.Register(r.Context(), account.RegisterInput(req), clientOf(r))
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	setSessionCookie(w, token, session.TTL)
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
	user, token, err := s.accounts.Login(r.Context(), req.Login, req.Password, clientOf(r))
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	setSessionCookie(w, token, session.TTL)
	writeJSON(w, http.StatusOK, sessionResponse{Token: token, User: toUserResponse(user)})
}

func (s *server) logout(w http.ResponseWriter, r *http.Request) {
	if err := session.Delete(r.Context(), s.q, current(r).sessionID); err != nil {
		internalError(w, r, err)
		return
	}
	clearSessionCookie(w)
	w.WriteHeader(http.StatusNoContent)
}

func (s *server) me(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, toUserResponse(current(r).user))
}
