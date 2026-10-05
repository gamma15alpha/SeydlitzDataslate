package api

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

type sessionInfo struct {
	ID         uuid.UUID `json:"id"`
	CreatedAt  time.Time `json:"createdAt"`
	LastUsedAt time.Time `json:"lastUsedAt"`
	UserAgent  string    `json:"userAgent"`
	Current    bool      `json:"current"`
}

func (s *server) listSessions(w http.ResponseWriter, r *http.Request) {
	cur := currentSession(r)
	rows, err := s.q.ListUserSessions(r.Context(), dbq.ListUserSessionsParams{
		UserID: cur.user.ID, CreatedAfter: time.Now().Add(-sessionMaxAge),
	})
	if err != nil {
		internalError(w, r, err)
		return
	}
	list := make([]sessionInfo, 0, len(rows))
	for _, row := range rows {
		list = append(list, sessionInfo{
			ID: row.ID, CreatedAt: row.CreatedAt, LastUsedAt: row.LastUsedAt, UserAgent: row.UserAgent, Current: row.ID == cur.id,
		})
	}
	writeJSON(w, http.StatusOK, list)
}

func (s *server) deleteSession(w http.ResponseWriter, r *http.Request) {
	cur := currentSession(r)
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "not_found", "session not found")
		return
	}
	n, err := s.q.DeleteUserSession(r.Context(), dbq.DeleteUserSessionParams{ID: id, UserID: cur.user.ID})
	if err != nil {
		internalError(w, r, err)
		return
	}
	if n == 0 {
		writeError(w, http.StatusNotFound, "not_found", "session not found")
		return
	}
	if id == cur.id {
		clearSessionCookie(w)
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *server) deleteOtherSessions(w http.ResponseWriter, r *http.Request) {
	cur := currentSession(r)
	if _, err := s.q.DeleteOtherSessions(r.Context(), dbq.DeleteOtherSessionsParams{UserID: cur.user.ID, KeepID: cur.id}); err != nil {
		internalError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
