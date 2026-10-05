package api

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/invite"
)

type inviteResponse struct {
	ID        uuid.UUID  `json:"id"`
	Code      string     `json:"code,omitempty"` // только при создании
	CreatedAt time.Time  `json:"createdAt"`
	ExpiresAt time.Time  `json:"expiresAt"`
	UsedAt    *time.Time `json:"usedAt,omitempty"`
	UsedBy    *string    `json:"usedBy,omitempty"`
}

func toInviteResponse(i invite.Invite, code string) inviteResponse {
	return inviteResponse{ID: i.ID, Code: code, CreatedAt: i.CreatedAt, ExpiresAt: i.ExpiresAt, UsedAt: i.UsedAt, UsedBy: i.UsedBy}
}

type createInviteRequest struct {
	ExpiresInDays int `json:"expiresInDays"`
}

func (s *server) createInvite(w http.ResponseWriter, r *http.Request) {
	req := createInviteRequest{ExpiresInDays: invite.DefaultDays}
	if r.ContentLength != 0 && !decodeJSON(w, r, &req) {
		return
	}
	created, code, err := invite.Create(r.Context(), s.q, current(r).user.ID, req.ExpiresInDays)
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	writeJSON(w, http.StatusCreated, toInviteResponse(created, code))
}

func (s *server) listInvites(w http.ResponseWriter, r *http.Request) {
	list, err := invite.List(r.Context(), s.q)
	if err != nil {
		internalError(w, r, err)
		return
	}
	invites := make([]inviteResponse, 0, len(list))
	for _, i := range list {
		invites = append(invites, toInviteResponse(i, ""))
	}
	writeJSON(w, http.StatusOK, invites)
}

func (s *server) deleteInvite(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "not_found", "invite not found")
		return
	}
	if err := invite.Revoke(r.Context(), s.q, id); err != nil {
		writeServiceError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
