// Package api — HTTP API сервера Seydlitz Dataslate.
//
// Сервер отвечает за синхронизацию персонажей и раздачу пакетов контента.
// Контракт — schemas/openapi.yaml (OpenAPI 3.1, ссылается на JSON Schema из той же папки);
// тест проверяет, что маршруты здесь и в спецификации совпадают.
package api

import (
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

// Version — версия сервера; задаётся при сборке: -ldflags "-X .../internal/api.Version=1.2.3".
var Version = "dev"

// Config — зависимости и настройки API.
type Config struct {
	Pool *pgxpool.Pool
	// TrustProxy — брать IP клиента из X-Real-IP / X-Forwarded-For. Включать, только если
	// сервер доступен исключительно через обратный прокси, который эти заголовки выставляет.
	TrustProxy bool
}

type server struct {
	pool *pgxpool.Pool
	q    *dbq.Queries
	// Неудачные попытки входа и регистрации: по IP — против перебора вообще,
	// по логину — против перебора пароля одного пользователя с разных адресов.
	ipLimiter    *auth.Limiter
	loginLimiter *auth.Limiter
}

// NewHandler собирает маршруты API. Все маршруты — под /api/, чтобы обратный прокси
// отдавал веб-сборку и API с одного домена (без CORS).
func NewHandler(cfg Config) http.Handler {
	s := &server{
		pool:         cfg.Pool,
		q:            dbq.New(cfg.Pool),
		ipLimiter:    auth.NewLimiter(30, 15*time.Minute),
		loginLimiter: auth.NewLimiter(10, 15*time.Minute),
	}

	r := chi.NewRouter()
	if cfg.TrustProxy {
		r.Use(middleware.RealIP)
	}
	r.NotFound(func(w http.ResponseWriter, _ *http.Request) {
		writeError(w, http.StatusNotFound, "not_found", "Not Found")
	})
	// Свой обработчик 405 в chi теряет заголовок Allow — собираем его сами.
	r.MethodNotAllowed(func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Allow", strings.Join(allowedMethods(r, req.URL.Path), ", "))
		writeError(w, http.StatusMethodNotAllowed, "method_not_allowed", "Method Not Allowed")
	})

	r.Route("/api", func(r chi.Router) {
		r.Get("/health", health)
		r.Post("/auth/register", s.register)
		r.Post("/auth/login", s.login)

		r.Group(func(r chi.Router) {
			r.Use(s.requireUser)
			r.Post("/auth/logout", s.logout)
			r.Get("/me", s.me)

			r.Group(func(r chi.Router) {
				r.Use(requireAdmin)
				r.Get("/invites", s.listInvites)
				r.Post("/invites", s.createInvite)
				r.Delete("/invites/{id}", s.deleteInvite)
			})
		})
	})
	return r
}

var methods = []string{
	http.MethodGet, http.MethodHead, http.MethodPost, http.MethodPut,
	http.MethodPatch, http.MethodDelete, http.MethodOptions,
}

func allowedMethods(r *chi.Mux, path string) []string {
	var allowed []string
	for _, m := range methods {
		if r.Match(chi.NewRouteContext(), m, path) {
			allowed = append(allowed, m)
		}
	}
	return allowed
}

type healthResponse struct {
	Status  string `json:"status"`
	Version string `json:"version"`
}

func health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, healthResponse{Status: "ok", Version: Version})
}
