package api

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

const (
	defaultInviteDays = 7
	maxInviteDays     = 90
)

type inviteResponse struct {
	ID        uuid.UUID  `json:"id"`
	Code      string     `json:"code,omitempty"` // только в ответе на создание: в базе кода нет
	CreatedAt time.Time  `json:"createdAt"`
	ExpiresAt time.Time  `json:"expiresAt"`
	UsedAt    *time.Time `json:"usedAt,omitempty"`
	UsedBy    *string    `json:"usedBy,omitempty"`
}

type createInviteRequest struct {
	ExpiresInDays int `json:"expiresInDays"`
}

func (s *server) createInvite(w http.ResponseWriter, r *http.Request) {
	req := createInviteRequest{ExpiresInDays: defaultInviteDays}
	if r.ContentLength != 0 && !decodeJSON(w, r, &req) {
		return
	}
	if req.ExpiresInDays < 1 || req.ExpiresInDays > maxInviteDays {
		writeError(w, http.StatusBadRequest, "invalid_expiry", "expiresInDays must be between 1 and 90")
		return
	}
	code, hash := auth.NewSecret()
	row, err := s.q.CreateInvite(r.Context(), dbq.CreateInviteParams{
		CodeHash:  hash,
		CreatedBy: currentSession(r).user.ID,
		ExpiresAt: time.Now().Add(time.Duration(req.ExpiresInDays) * 24 * time.Hour),
	})
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusCreated, inviteResponse{ID: row.ID, Code: code, CreatedAt: row.CreatedAt, ExpiresAt: row.ExpiresAt})
}

func (s *server) listInvites(w http.ResponseWriter, r *http.Request) {
	rows, err := s.q.ListInvites(r.Context())
	if err != nil {
		internalError(w, r, err)
		return
	}
	invites := make([]inviteResponse, 0, len(rows))
	for _, row := range rows {
		invites = append(invites, inviteResponse{
			ID: row.ID, CreatedAt: row.CreatedAt, ExpiresAt: row.ExpiresAt, UsedAt: row.UsedAt, UsedBy: row.UsedByLogin,
		})
	}
	writeJSON(w, http.StatusOK, invites)
}

func (s *server) deleteInvite(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "not_found", "invite not found")
		return
	}
	n, err := s.q.DeleteUnusedInvite(r.Context(), id)
	if err != nil {
		internalError(w, r, err)
		return
	}
	if n == 0 {
		writeError(w, http.StatusNotFound, "not_found", "invite not found or already used")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
