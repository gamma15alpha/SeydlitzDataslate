package api

import "net/http"

type changeLoginRequest struct {
	Login    string `json:"login"`
	Password string `json:"password"`
}

func (s *server) changeLogin(w http.ResponseWriter, r *http.Request) {
	var req changeLoginRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	user, err := s.accounts.ChangeLogin(r.Context(), current(r).user, req.Login, req.Password, clientOf(r))
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, toUserResponse(user))
}

type changePasswordRequest struct {
	CurrentPassword string `json:"currentPassword"`
	NewPassword     string `json:"newPassword"`
}

func (s *server) changePassword(w http.ResponseWriter, r *http.Request) {
	var req changePasswordRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	cur := current(r)
	if err := s.accounts.ChangePassword(r.Context(), cur.user, cur.sessionID, req.CurrentPassword, req.NewPassword, clientOf(r)); err != nil {
		writeServiceError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
