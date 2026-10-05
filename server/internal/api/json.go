package api

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"mime"
	"net/http"
	"unicode/utf8"
)

const maxBodyBytes = 64 << 10

type errorResponse struct {
	Error string `json:"error"`
	Code  string `json:"code"`
}

func writeError(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, errorResponse{Error: msg, Code: code})
}

func internalError(w http.ResponseWriter, r *http.Request, err error) {
	slog.ErrorContext(r.Context(), "request failed", "err", err)
	writeError(w, http.StatusInternalServerError, "internal", "Internal Server Error")
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

// decodeJSON при ошибке сам пишет ответ. Требование application/json заодно защищает от CSRF через HTML-формы.
func decodeJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	if mt, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type")); mt != "application/json" {
		writeError(w, http.StatusUnsupportedMediaType, "unsupported_media_type", "Content-Type must be application/json")
		return false
	}
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxBodyBytes))
	var tooLarge *http.MaxBytesError
	if errors.As(err, &tooLarge) {
		writeError(w, http.StatusRequestEntityTooLarge, "too_large", "request body is too large")
		return false
	}
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "read body: "+err.Error())
		return false
	}
	// encoding/json молча заменил бы битые байты на U+FFFD.
	if !utf8.Valid(body) {
		writeError(w, http.StatusBadRequest, "bad_request", "request body is not valid UTF-8")
		return false
	}
	if err := json.Unmarshal(body, dst); err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid JSON body: "+err.Error())
		return false
	}
	return true
}
