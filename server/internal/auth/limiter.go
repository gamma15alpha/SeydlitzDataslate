package auth

import (
	"sync"
	"time"
)

// Limiter считает неудачные попытки по ключу в фиксированном окне; счётчики в памяти.
type Limiter struct {
	max    int
	window time.Duration
	now    func() time.Time

	mu      sync.Mutex
	entries map[string]limiterEntry
}

type limiterEntry struct {
	count int
	start time.Time
}

func NewLimiter(max int, window time.Duration) *Limiter {
	return &Limiter{max: max, window: window, now: time.Now, entries: map[string]limiterEntry{}}
}

// Blocked также возвращает время до сброса.
func (l *Limiter) Blocked(key string) (bool, time.Duration) {
	l.mu.Lock()
	defer l.mu.Unlock()
	e, ok := l.entries[key]
	if !ok {
		return false, 0
	}
	left := e.start.Add(l.window).Sub(l.now())
	if left <= 0 {
		delete(l.entries, key)
		return false, 0
	}
	return e.count >= l.max, left
}

func (l *Limiter) Fail(key string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	if len(l.entries) >= 4096 {
		l.prune(now)
	}
	e, ok := l.entries[key]
	if !ok || now.Sub(e.start) >= l.window {
		e = limiterEntry{start: now}
	}
	e.count++
	l.entries[key] = e
}

func (l *Limiter) Reset(key string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	delete(l.entries, key)
}

func (l *Limiter) prune(now time.Time) {
	for k, e := range l.entries {
		if now.Sub(e.start) >= l.window {
			delete(l.entries, k)
		}
	}
}
