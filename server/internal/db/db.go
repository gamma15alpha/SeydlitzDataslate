// Package db — подключение к PostgreSQL и встроенные миграции goose.
package db

import (
	"context"
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"log/slog"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"
	"github.com/jackc/pgx/v5/tracelog"
	"github.com/pressly/goose/v3"
)

//go:embed migrations/*.sql
var migrations embed.FS

// Open подключается и применяет миграции.
func Open(ctx context.Context, url string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(url)
	if err != nil {
		return nil, fmt.Errorf("database url: %w", err)
	}
	return OpenConfig(ctx, cfg)
}

func OpenConfig(ctx context.Context, cfg *pgxpool.Config) (*pgxpool.Pool, error) {
	if slog.Default().Enabled(ctx, slog.LevelDebug) {
		cfg.ConnConfig.Tracer = &tracelog.TraceLog{Logger: tracelog.LoggerFunc(logQuery), LogLevel: tracelog.LogLevelInfo}
	}
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("connect: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("connect: %w", err)
	}
	if err := migrate(ctx, pool); err != nil {
		pool.Close()
		return nil, err
	}
	return pool, nil
}

func migrate(ctx context.Context, pool *pgxpool.Pool) error {
	dir, err := fs.Sub(migrations, "migrations")
	if err != nil {
		return err
	}
	sqlDB := stdlib.OpenDBFromPool(pool)
	defer sqlDB.Close()

	provider, err := goose.NewProvider(goose.DialectPostgres, sqlDB, dir)
	if err != nil {
		return fmt.Errorf("migrations: %w", err)
	}
	if _, err := provider.Up(ctx); err != nil {
		return fmt.Errorf("migrations: %w", err)
	}
	return nil
}

func IsUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}

// logQuery не пишет аргументы: в них хеши паролей и токенов.
func logQuery(ctx context.Context, _ tracelog.LogLevel, msg string, data map[string]any) {
	if msg == "Prepare" && data["err"] == nil {
		return // раз на соединение — шум
	}
	var attrs []slog.Attr
	if sql, ok := data["sql"].(string); ok {
		attrs = append(attrs, slog.String("query", queryName(sql)))
	}
	if d, ok := data["time"].(time.Duration); ok {
		attrs = append(attrs, slog.Float64("duration_ms", float64(d.Microseconds())/1000))
	}
	if tag, ok := data["commandTag"].(string); ok {
		attrs = append(attrs, slog.String("tag", tag))
	}
	if err, ok := data["err"].(error); ok {
		attrs = append(attrs, slog.String("err", err.Error()))
	}
	slog.LogAttrs(ctx, slog.LevelDebug, "db "+strings.ToLower(msg), attrs...)
}

// queryName — имя запроса sqlc или SQL в одну строку.
func queryName(sql string) string {
	if rest, ok := strings.CutPrefix(sql, "-- name: "); ok {
		name, _, _ := strings.Cut(rest, " ")
		return name
	}
	return strings.Join(strings.Fields(sql), " ")
}
