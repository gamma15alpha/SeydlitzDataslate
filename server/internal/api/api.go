// Package api — HTTP API сервера Seydlitz Dataslate.
//
// Сервер отвечает за синхронизацию персонажей и раздачу пакетов контента
// (контракт — JSON Schema в /schemas). Сейчас — каркас: только проверка работоспособности.
package api

import (
	"encoding/json"
	"net/http"
)

// Version — версия сервера; задаётся при сборке: -ldflags "-X .../internal/api.Version=1.2.3".
var Version = "dev"

// NewHandler собирает маршруты API. Все маршруты — под /api/, чтобы обратный прокси
// отдавал веб-сборку и API с одного домена (без CORS).
func NewHandler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/health", health)
	return mux
}

type healthResponse struct {
	Status  string `json:"status"`
	Version string `json:"version"`
}

func health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, healthResponse{Status: "ok", Version: Version})
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
