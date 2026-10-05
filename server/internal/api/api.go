// Package api — HTTP API сервера; контракт — schemas/openapi.yaml.
// Только перевод между HTTP и сценариями (account, session, invite): разбор запроса, вызов, ошибка → статус и код.
package api

import (
	"cmp"
	"net/http"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/account"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

// Version задаётся при сборке через -ldflags -X.
var Version = "dev"

type Config struct {
	Pool *pgxpool.Pool
	// TrustProxy: доверять X-Real-IP, X-Forwarded-For и X-Request-ID — только за прокси, который их перезаписывает.
	TrustProxy bool

	// Запросов в минуту, 0 — по умолчанию (600 и 300). По IP с запасом: игроки за одним столом часто за общим NAT.
	IPRequestsPerMinute   int
	UserRequestsPerMinute int
}

type server struct {
	q        *dbq.Queries
	accounts *account.Service
}

// Все маршруты под /api/: веб и API на одном домене, без CORS.
func NewHandler(cfg Config) http.Handler {
	s := &server{q: dbq.New(cfg.Pool), accounts: account.New(cfg.Pool)}

	r := chi.NewRouter()
	if cfg.TrustProxy {
		r.Use(middleware.RealIP)
	}
	r.Use(requestLogger(cfg.TrustProxy))
	r.Use(rateLimit(cmp.Or(cfg.IPRequestsPerMinute, 600), ipKey))
	r.NotFound(func(w http.ResponseWriter, _ *http.Request) {
		writeError(w, http.StatusNotFound, "not_found", "Not Found")
	})
	// Свой обработчик 405 в chi не ставит Allow.
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
			r.Use(rateLimit(cmp.Or(cfg.UserRequestsPerMinute, 300), userKey))
			r.Post("/auth/logout", s.logout)
			r.Get("/me", s.me)
			r.Put("/me/login", s.changeLogin)
			r.Put("/me/password", s.changePassword)
			r.Get("/sessions", s.listSessions)
			r.Delete("/sessions", s.deleteOtherSessions)
			r.Delete("/sessions/{id}", s.deleteSession)

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
