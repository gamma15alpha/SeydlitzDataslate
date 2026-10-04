# Сервер

Go, только стандартная библиотека. Будет отвечать за синхронизацию персонажей и раздачу пакетов контента; форматы данных — JSON Schema в `../schemas/`.

## Команды

```
go run ./cmd/server       # http://127.0.0.1:8090/api/health
go test ./...
go build -o bin/server -ldflags "-X github.com/gamma15alpha/SeydlitzDataslate/server/internal/api.Version=0.1.0" ./cmd/server
```

## Устройство

- `cmd/server` — точка входа: адрес из переменной `ADDR` (по умолчанию `127.0.0.1:8090` — наружу сервер выставляет обратный прокси), корректная остановка по SIGINT/SIGTERM, логи через `log/slog`.
- `internal/api` — маршруты. Все — под `/api/`, чтобы веб-сборка и API жили на одном домене без CORS.

| Маршрут | Ответ |
|---|---|
| `GET /api/health` | `{"status":"ok","version":"…"}` |
