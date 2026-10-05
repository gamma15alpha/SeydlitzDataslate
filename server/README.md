# Сервер

Go, роутер [chi](https://github.com/go-chi/chi), PostgreSQL 18 через [pgx](https://github.com/jackc/pgx). Запросы — [sqlc](https://sqlc.dev), миграции — [goose](https://github.com/pressly/goose). Отвечает за аккаунты, синхронизацию персонажей и раздачу пакетов контента.

Контракт API — [`../schemas/openapi.yaml`](../schemas/openapi.yaml) (OpenAPI 3.1); форматы данных — JSON Schema в той же папке. Тест `TestRoutesMatchSpec` падает, если маршруты сервера и спецификация расходятся: новый маршрут сначала описывается в спецификации.

## Команды

```
docker compose up -d                 
export DATABASE_URL=postgres://dataslate:dataslate@127.0.0.1:5432/dataslate

go run ./cmd/server                  
go run ./cmd/server admin create root # первый администратор; пароль спросит без эха

TEST_DATABASE_URL=$DATABASE_URL go test ./...  
go tool -modfile=tools.mod sqlc generate        
go build -o bin/server -ldflags "-X github.com/gamma15alpha/SeydlitzDataslate/server/internal/api.Version=0.1.0" ./cmd/server
```

sqlc закреплён в отдельном `tools.mod`, чтобы его зависимости не попадали в `go.mod` сервера.

## Переменные окружения

| Переменная | Значение |
|---|---|
| `DATABASE_URL` | обязательна: `postgres://user:pass@host:5432/db` |
| `ADDR` | адрес HTTP-сервера; по умолчанию `127.0.0.1:8090` — наружу сервер выставляет обратный прокси |
| `TRUST_PROXY` | `1` — брать IP клиента из `X-Real-IP` / `X-Forwarded-For` (для лимита попыток входа). Только если сервер доступен исключительно через прокси, который эти заголовки перезаписывает |

## Аккаунты

- Регистрация — только по инвайту; инвайты создаёт администратор (`POST /api/invites`), первого администратора — CLI.
- Сессия — случайный токен на 30 дней, продлевается при использовании. Веб получает его в cookie (`HttpOnly`, `Secure`, `SameSite=Lax`), Android — в теле ответа, и шлёт в `Authorization: Bearer`. В базе хранятся только SHA-256 токенов и кодов инвайтов.
- Неудачные попытки входа и регистрации ограничены: 30 за 15 минут с одного IP, 10 за 15 минут на один логин; дальше — `429` с `Retry-After`.

Маршруты и форматы — в спецификации. Ошибки — JSON `{"error":"…","code":"…"}`: `error` — для человека, `code` — для клиента (`login_taken`, `invalid_invite`, …). Тело запроса — только JSON в UTF-8.
