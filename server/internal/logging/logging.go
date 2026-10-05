// Package logging — slog с атрибутами запроса (request_id, user_id) из контекста.
package logging

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"strings"
)

// RequestInfo заполняется по ходу запроса: UserID — после проверки сессии.
type RequestInfo struct {
	ID     string
	UserID string
}

type requestKey struct{}

func WithRequest(ctx context.Context, info *RequestInfo) context.Context {
	return context.WithValue(ctx, requestKey{}, info)
}

func Request(ctx context.Context) *RequestInfo {
	info, _ := ctx.Value(requestKey{}).(*RequestInfo)
	return info
}

// New: format — text или json; level — debug, info, warn или error.
func New(w io.Writer, format, level string) (*slog.Logger, error) {
	var lvl slog.Level
	if err := lvl.UnmarshalText([]byte(level)); err != nil {
		return nil, fmt.Errorf("log level %q: want debug, info, warn or error", level)
	}
	opts := &slog.HandlerOptions{Level: lvl}
	var h slog.Handler
	switch strings.ToLower(format) {
	case "text":
		h = slog.NewTextHandler(w, opts)
	case "json":
		h = slog.NewJSONHandler(w, opts)
	default:
		return nil, fmt.Errorf("log format %q: want text or json", format)
	}
	return slog.New(contextHandler{h}), nil
}

type contextHandler struct{ slog.Handler }

func (h contextHandler) Handle(ctx context.Context, r slog.Record) error {
	if info := Request(ctx); info != nil {
		r.AddAttrs(slog.String("request_id", info.ID))
		if info.UserID != "" {
			r.AddAttrs(slog.String("user_id", info.UserID))
		}
	}
	return h.Handler.Handle(ctx, r)
}

func (h contextHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return contextHandler{h.Handler.WithAttrs(attrs)}
}

func (h contextHandler) WithGroup(name string) slog.Handler {
	return contextHandler{h.Handler.WithGroup(name)}
}
