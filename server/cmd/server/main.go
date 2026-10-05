// Сервер Seydlitz Dataslate.
//
//	server                       запустить HTTP-сервер (то же, что server serve)
//	server admin create <login>  создать администратора; пароль — из терминала или stdin
//
// Переменные окружения:
//
//	DATABASE_URL  postgres://user:pass@host:5432/db (обязательна)
//	ADDR          адрес HTTP-сервера, по умолчанию 127.0.0.1:8090 (наружу — через обратный прокси)
//	TRUST_PROXY   1 — брать IP клиента из X-Real-IP / X-Forwarded-For (только за прокси)
package main

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/term"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/api"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

const usage = `usage:
  server                       start the HTTP server
  server serve                 same
  server admin create <login>  create an administrator`

func main() {
	if err := run(os.Args[1:]); err != nil {
		slog.Error("server failed", "err", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	switch {
	case len(args) == 0 || len(args) == 1 && args[0] == "serve":
		return serve(ctx)
	case len(args) == 3 && args[0] == "admin" && args[1] == "create":
		return createAdmin(ctx, args[2])
	default:
		fmt.Fprintln(os.Stderr, usage)
		return errors.New("unknown command")
	}
}

func openDB(ctx context.Context) (*pgxpool.Pool, error) {
	url := os.Getenv("DATABASE_URL")
	if url == "" {
		return nil, errors.New("DATABASE_URL is not set")
	}
	return db.Open(ctx, url)
}

// serve работает до сигнала остановки или ошибки сервера; os.Exit — только в main,
// чтобы отложенные вызовы здесь успели выполниться.
func serve(ctx context.Context) error {
	addr := os.Getenv("ADDR")
	if addr == "" {
		addr = "127.0.0.1:8090"
	}
	pool, err := openDB(ctx)
	if err != nil {
		return err
	}
	defer pool.Close()

	server := &http.Server{
		Addr:              addr,
		Handler:           api.NewHandler(api.Config{Pool: pool, TrustProxy: os.Getenv("TRUST_PROXY") == "1"}),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	go cleanupSessions(ctx, dbq.New(pool))

	serveErr := make(chan error, 1)
	go func() {
		slog.Info("server started", "addr", addr, "version", api.Version)
		serveErr <- server.ListenAndServe()
	}()

	select {
	case err := <-serveErr:
		return err // ListenAndServe возвращает ErrServerClosed только после Shutdown
	case <-ctx.Done():
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("shutdown: %w", err)
	}
	if err := <-serveErr; !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	slog.Info("server stopped")
	return nil
}

// cleanupSessions раз в час удаляет истёкшие сессии.
func cleanupSessions(ctx context.Context, q *dbq.Queries) {
	ticker := time.NewTicker(time.Hour)
	defer ticker.Stop()
	for {
		n, err := q.DeleteExpiredSessions(ctx)
		if err != nil && ctx.Err() == nil {
			slog.Error("cleanup sessions", "err", err)
		} else if n > 0 {
			slog.Info("expired sessions removed", "count", n)
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func createAdmin(ctx context.Context, login string) error {
	if msg := auth.ValidateLogin(login); msg != "" {
		return errors.New(msg)
	}
	password, err := readPassword()
	if err != nil {
		return err
	}
	if msg := auth.ValidatePassword(password); msg != "" {
		return errors.New(msg)
	}
	pool, err := openDB(ctx)
	if err != nil {
		return err
	}
	defer pool.Close()

	user, err := dbq.New(pool).CreateUser(ctx, dbq.CreateUserParams{
		Login: login, DisplayName: login, PasswordHash: auth.HashPassword(password), IsAdmin: true,
	})
	if db.IsUniqueViolation(err) {
		return fmt.Errorf("login %q is already taken", login)
	}
	if err != nil {
		return err
	}
	fmt.Printf("administrator %s created (id %s)\n", user.Login, user.ID)
	return nil
}

// readPassword спрашивает пароль дважды без эха, если stdin — терминал,
// иначе читает одну строку (для скриптов: echo "$PASSWORD" | server admin create …).
func readPassword() (string, error) {
	fd := int(os.Stdin.Fd())
	if !term.IsTerminal(fd) {
		line, err := bufio.NewReader(os.Stdin).ReadString('\n')
		if err != nil && line == "" {
			return "", fmt.Errorf("read password from stdin: %w", err)
		}
		return strings.TrimRight(line, "\r\n"), nil
	}
	fmt.Fprint(os.Stderr, "Password: ")
	first, err := term.ReadPassword(fd)
	fmt.Fprintln(os.Stderr)
	if err != nil {
		return "", err
	}
	fmt.Fprint(os.Stderr, "Repeat password: ")
	second, err := term.ReadPassword(fd)
	fmt.Fprintln(os.Stderr)
	if err != nil {
		return "", err
	}
	if string(first) != string(second) {
		return "", errors.New("passwords do not match")
	}
	return string(first), nil
}
