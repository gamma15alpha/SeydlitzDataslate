package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/character"
)

type characterResponse struct {
	ID        uuid.UUID       `json:"id"`
	Revision  int64           `json:"revision"`
	Deleted   bool            `json:"deleted"`
	UpdatedAt time.Time       `json:"updatedAt"`
	Character json.RawMessage `json:"character,omitempty"`
}

func toCharacterResponse(c character.Character, withData bool) characterResponse {
	resp := characterResponse{ID: c.ID, Revision: c.Revision, Deleted: c.Data == nil, UpdatedAt: c.UpdatedAt}
	if withData {
		resp.Character = c.Data
	}
	return resp
}

func revisionTag(revision int64) string {
	return `"` + strconv.FormatInt(revision, 10) + `"`
}

type characterChangesResponse struct {
	Cursor  int64               `json:"cursor"`
	Changes []characterResponse `json:"changes"`
}

func (s *server) listCharacterChanges(w http.ResponseWriter, r *http.Request) {
	var since int64
	if v := r.URL.Query().Get("since"); v != "" {
		n, err := strconv.ParseInt(v, 10, 64)
		if err != nil || n < 0 {
			writeError(w, http.StatusBadRequest, "bad_request", "since must be a non-negative integer")
			return
		}
		since = n
	}
	cursor, list, err := s.characters.Changes(r.Context(), current(r).user.ID, since)
	if err != nil {
		internalError(w, r, err)
		return
	}
	changes := make([]characterResponse, 0, len(list))
	for _, c := range list {
		changes = append(changes, toCharacterResponse(c, true))
	}
	writeJSON(w, http.StatusOK, characterChangesResponse{Cursor: cursor, Changes: changes})
}

// characterID при ошибке сам пишет ответ: кривой id — как отсутствующая анкета.
func characterID(w http.ResponseWriter, r *http.Request) (uuid.UUID, bool) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "not_found", character.ErrNotFound.Error())
		return uuid.Nil, false
	}
	return id, true
}

func (s *server) getCharacter(w http.ResponseWriter, r *http.Request) {
	id, ok := characterID(w, r)
	if !ok {
		return
	}
	c, err := s.characters.Get(r.Context(), current(r).user.ID, id)
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	w.Header().Set("ETag", revisionTag(c.Revision))
	writeJSON(w, http.StatusOK, toCharacterResponse(c, true))
}

// precondition — ревизия из If-Match или 0 для If-None-Match: * (только если create). При ошибке сам пишет ответ.
func precondition(w http.ResponseWriter, r *http.Request, create bool) (int64, bool) {
	ifMatch := r.Header.Get("If-Match")
	ifNoneMatch := r.Header.Get("If-None-Match")
	switch {
	case ifMatch != "" && ifNoneMatch == "":
		tag, quoted := strings.CutPrefix(ifMatch, `"`)
		tag, closed := strings.CutSuffix(tag, `"`)
		n, err := strconv.ParseInt(tag, 10, 64)
		if quoted && closed && err == nil && n > 0 {
			return n, true
		}
	case create && ifNoneMatch == "*" && ifMatch == "":
		return 0, true
	}
	msg := `If-Match: "<revision>" is required`
	if create {
		msg = `exactly one of If-Match: "<revision>" or If-None-Match: * is required`
	}
	writeError(w, http.StatusPreconditionRequired, "precondition_required", msg)
	return 0, false
}

func (s *server) putCharacter(w http.ResponseWriter, r *http.Request) {
	id, ok := characterID(w, r)
	if !ok {
		return
	}
	base, ok := precondition(w, r, true)
	if !ok {
		return
	}
	body, ok := readJSON(w, r, character.MaxBytes)
	if !ok {
		return
	}
	c, err := s.characters.Put(r.Context(), current(r).user.ID, id, base, body)
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	status := http.StatusOK
	if base == 0 {
		status = http.StatusCreated
	}
	w.Header().Set("ETag", revisionTag(c.Revision))
	writeJSON(w, status, toCharacterResponse(c, false))
}

func (s *server) deleteCharacter(w http.ResponseWriter, r *http.Request) {
	id, ok := characterID(w, r)
	if !ok {
		return
	}
	base, ok := precondition(w, r, false)
	if !ok {
		return
	}
	c, err := s.characters.Delete(r.Context(), current(r).user.ID, id, base)
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	w.Header().Set("ETag", revisionTag(c.Revision))
	writeJSON(w, http.StatusOK, toCharacterResponse(c, false))
}
