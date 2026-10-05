package api

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/session"
)

type sessionInfo struct {
	ID         uuid.UUID `json:"id"`
	CreatedAt  time.Time `json:"createdAt"`
	LastUsedAt time.Time `json:"lastUsedAt"`
	UserAgent  string    `json:"userAgent"`
	Current    bool      `json:"current"`
}

func (s *server) listSessions(w http.ResponseWriter, r *http.Request) {
	cur := current(r)
	infos, err := session.List(r.Context(), s.q, cur.user.ID, cur.sessionID, time.Now())
	if err != nil {
		internalError(w, r, err)
		return
	}
	list := make([]sessionInfo, 0, len(infos))
	for _, i := range infos {
		list = append(list, sessionInfo(i))
	}
	writeJSON(w, http.StatusOK, list)
}

func (s *server) deleteSession(w http.ResponseWriter, r *http.Request) {
	cur := current(r)
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "not_found", "session not found")
		return
	}
	if err := session.End(r.Context(), s.q, cur.user.ID, id); err != nil {
		writeServiceError(w, r, err)
		return
	}
	if id == cur.sessionID {
		clearSessionCookie(w)
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *server) deleteOtherSessions(w http.ResponseWriter, r *http.Request) {
	cur := current(r)
	if _, err := session.EndOthers(r.Context(), s.q, cur.user.ID, cur.sessionID); err != nil {
		internalError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
