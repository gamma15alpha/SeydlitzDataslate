package api

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/account"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/invite"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/session"
)

// writeServiceError переводит ошибку сценария в ответ: статус и код из контракта (schemas/openapi.yaml).
// Незнакомая ошибка — 500 с записью в лог.
func writeServiceError(w http.ResponseWriter, r *http.Request, err error) {
	var invalid *account.InvalidError
	var tooMany *account.TooManyAttemptsError
	switch {
	case errors.As(err, &invalid):
		writeError(w, http.StatusBadRequest, invalid.Code, invalid.Message)
	case errors.As(err, &tooMany):
		w.Header().Set("Retry-After", strconv.Itoa(int(tooMany.RetryAfter.Seconds())+1))
		writeError(w, http.StatusTooManyRequests, "too_many_attempts", "too many failed attempts, try again later")
	case errors.Is(err, account.ErrInvalidCredentials):
		unauthorized(w, "invalid_credentials", err.Error(), false)
	// Не 401: сессия действительна, клиент не должен разлогинивать.
	case errors.Is(err, account.ErrWrongPassword):
		writeError(w, http.StatusForbidden, "wrong_password", err.Error())
	case errors.Is(err, account.ErrLoginTaken):
		writeError(w, http.StatusConflict, "login_taken", err.Error())
	case errors.Is(err, invite.ErrInvalid):
		writeError(w, http.StatusBadRequest, "invalid_invite", err.Error())
	case errors.Is(err, invite.ErrInvalidExpiry):
		writeError(w, http.StatusBadRequest, "invalid_expiry", err.Error())
	case errors.Is(err, invite.ErrNotFound), errors.Is(err, session.ErrNotFound):
		writeError(w, http.StatusNotFound, "not_found", err.Error())
	default:
		internalError(w, r, err)
	}
}
