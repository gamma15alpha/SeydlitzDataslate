package api

import (
	"encoding/hex"
	"errors"
	"io"
	"mime"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/blob"
)

func (s *server) setAvatar(w http.ResponseWriter, r *http.Request) {
	data, ok := readImage(w, r)
	if !ok {
		return
	}
	user, err := s.accounts.SetAvatar(r.Context(), current(r).user, data)
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, toUserResponse(user))
}

func (s *server) removeAvatar(w http.ResponseWriter, r *http.Request) {
	user, err := s.accounts.RemoveAvatar(r.Context(), current(r).user)
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, toUserResponse(user))
}

// readImage при ошибке сам пишет ответ. Тип image/* заодно защищает от CSRF: HTML-форма его не отправит.
func readImage(w http.ResponseWriter, r *http.Request) ([]byte, bool) {
	switch mt, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type")); mt {
	case "image/png", "image/jpeg", "image/webp":
	default:
		writeError(w, http.StatusUnsupportedMediaType, "unsupported_media_type", "Content-Type must be image/png, image/jpeg or image/webp")
		return nil, false
	}
	data, err := io.ReadAll(http.MaxBytesReader(w, r.Body, blob.MaxUploadBytes))
	var tooLarge *http.MaxBytesError
	if errors.As(err, &tooLarge) {
		writeError(w, http.StatusRequestEntityTooLarge, "too_large", "request body is too large")
		return nil, false
	}
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "read body: "+err.Error())
		return nil, false
	}
	return data, true
}

func (s *server) getBlob(w http.ResponseWriter, r *http.Request) {
	param := chi.URLParam(r, "sha256")
	sum, err := hex.DecodeString(param)
	if err != nil || len(sum) != 32 || hex.EncodeToString(sum) != param {
		writeError(w, http.StatusNotFound, "not_found", blob.ErrNotFound.Error())
		return
	}
	etag := `"` + param + `"`
	cache := func() {
		// Под хешем содержимое не меняется — кэш вечный; private: только с сессией.
		w.Header().Set("Cache-Control", "private, max-age=31536000, immutable")
		w.Header().Set("ETag", etag)
	}
	if r.Header.Get("If-None-Match") == etag {
		cache()
		w.WriteHeader(http.StatusNotModified)
		return
	}
	b, err := blob.Get(r.Context(), s.q, sum)
	if err != nil {
		writeServiceError(w, r, err)
		return
	}
	cache()
	w.Header().Set("Content-Type", b.Mime)
	w.Header().Set("Content-Length", strconv.Itoa(len(b.Bytes)))
	w.Header().Set("X-Content-Type-Options", "nosniff")
	_, _ = w.Write(b.Bytes)
}
