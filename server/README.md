# Сервер

Go, [chi](https://github.com/go-chi/chi), PostgreSQL 18 через [pgx](https://github.com/jackc/pgx), [sqlc](https://sqlc.dev), [goose](https://github.com/pressly/goose). Контракт API — [`../schemas/openapi.yaml`](../schemas/openapi.yaml); `TestRoutesMatchSpec` падает, если маршруты и спецификация расходятся.

```
docker compose up -d
export DATABASE_URL=postgres://dataslate:dataslate@127.0.0.1:5432/dataslate

go run ./cmd/server
go run ./cmd/server admin create root

TEST_DATABASE_URL=$DATABASE_URL go test ./...
go tool -modfile=tools.mod sqlc generate
go build -o bin/server -ldflags "-X github.com/gamma15alpha/SeydlitzDataslate/server/internal/api.Version=0.1.0" ./cmd/server
```

`admin create` создаёт первого администратора, регистрация остальных — по инвайтам.

| Переменная | Значение |
|---|---|
| `DATABASE_URL` | обязательна |
| `ADDR` | по умолчанию `127.0.0.1:8090` |
| `TRUST_PROXY` | `1` — IP клиента и ID запроса из заголовков прокси; только за прокси, который их перезаписывает |
| `LOG_FORMAT` | `text` (по умолчанию) или `json` |
| `LOG_LEVEL` | `debug` (с SQL-запросами), `info` (по умолчанию), `warn`, `error` |

```
cmd/server/        запуск, CLI
internal/account/  учётные записи
internal/session/  сессии
internal/invite/   инвайты
internal/blob/     картинки по SHA-256, автоочистка
internal/character/ анкеты: ревизии, If-Match, надгробия, курсор изменений, история
internal/api/      HTTP; ошибки сценариев → статусы (errors.go)
internal/auth/     argon2id, секреты, лимитер
internal/db/       подключение, миграции; dbq/ — код sqlc
internal/logging/  slog, ID запроса
```
