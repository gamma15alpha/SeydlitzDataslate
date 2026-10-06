// Package account — учётные записи: регистрация по инвайту, вход, смена логина и пароля, аватар, создание администратора.
// Про HTTP не знает: ошибки — значения ниже, их перевод в статусы и коды — забота api.
package account

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/auth"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/blob"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/invite"
	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/session"
)

var (
	ErrLoginTaken = errors.New("login is already taken")
	// ErrInvalidCredentials — неверный логин или пароль; что именно — не уточняется.
	ErrInvalidCredentials = errors.New("invalid login or password")
	// ErrWrongPassword — неверный текущий пароль при изменении учётной записи; сессия при этом действительна.
	ErrWrongPassword = errors.New("current password is incorrect")
)

// InvalidError — значение не проходит правила; Code — машиночитаемая причина (invalid_login, …).
type InvalidError struct {
	Code    string
	Message string
}

func (e *InvalidError) Error() string { return e.Message }

// TooManyAttemptsError — лимит неудачных попыток исчерпан; повторить — через RetryAfter.
type TooManyAttemptsError struct {
	RetryAfter time.Duration
}

func (e *TooManyAttemptsError) Error() string {
	return fmt.Sprintf("too many failed attempts, retry in %s", e.RetryAfter.Round(time.Second))
}

// Client — откуда запрос: IP (для лимитов; IPv6 — уже сведённый к /64) и User-Agent (для списка сессий).
type Client struct {
	IP        string
	UserAgent string
}

type Service struct {
	pool *pgxpool.Pool
	q    *dbq.Queries
	// Неудачные попытки: по IP и по логину (перебор пароля с разных адресов). Счётчики в памяти процесса.
	ipLimiter    *auth.Limiter
	loginLimiter *auth.Limiter
}

func New(pool *pgxpool.Pool) *Service {
	return &Service{
		pool:         pool,
		q:            dbq.New(pool),
		ipLimiter:    auth.NewLimiter(30, 15*time.Minute),
		loginLimiter: auth.NewLimiter(10, 15*time.Minute),
	}
}

func ValidateLogin(login string) error {
	if len(login) < 3 || len(login) > 32 {
		return &InvalidError{"invalid_login", "login must be 3 to 32 characters long"}
	}
	for _, c := range login {
		if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-') {
			return &InvalidError{"invalid_login", "login may contain only latin letters, digits, '_', '.' and '-'"}
		}
	}
	return nil
}

func ValidatePassword(password string) error {
	if n := utf8.RuneCountInString(password); n < 8 || n > 128 {
		return &InvalidError{"invalid_password", "password must be 8 to 128 characters long"}
	}
	return nil
}

func validateDisplayName(name string) error {
	if utf8.RuneCountInString(name) > 64 {
		return &InvalidError{"invalid_display_name", "display name must be at most 64 characters long"}
	}
	return nil
}

func (s *Service) checkLimits(keys ...limitKey) error {
	for _, k := range keys {
		if blocked, retry := k.limiter.Blocked(k.key); blocked {
			return &TooManyAttemptsError{RetryAfter: retry}
		}
	}
	return nil
}

type limitKey struct {
	limiter *auth.Limiter
	key     string
}

type RegisterInput struct {
	Invite      string
	Login       string
	Password    string
	DisplayName string // пусто — как логин
}

// Register создаёт пользователя по инвайту и открывает ему сессию. Неверный инвайт — неудачная попытка с этого IP.
func (s *Service) Register(ctx context.Context, in RegisterInput, client Client) (dbq.User, string, error) {
	if err := s.checkLimits(limitKey{s.ipLimiter, client.IP}); err != nil {
		return dbq.User{}, "", err
	}
	login := strings.TrimSpace(in.Login)
	displayName := strings.TrimSpace(in.DisplayName)
	if displayName == "" {
		displayName = login
	}
	if err := firstErr(ValidateLogin(login), ValidatePassword(in.Password), validateDisplayName(displayName)); err != nil {
		return dbq.User{}, "", err
	}
	passwordHash := auth.HashPassword(in.Password) // до транзакции: argon2 медленный

	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return dbq.User{}, "", err
	}
	defer tx.Rollback(ctx)
	q := s.q.WithTx(tx)

	inviteID, err := invite.Claim(ctx, q, in.Invite)
	if errors.Is(err, invite.ErrInvalid) {
		s.ipLimiter.Fail(client.IP)
		return dbq.User{}, "", err
	}
	if err != nil {
		return dbq.User{}, "", err
	}
	user, err := q.CreateUser(ctx, dbq.CreateUserParams{Login: login, DisplayName: displayName, PasswordHash: passwordHash})
	if db.IsUniqueViolation(err) {
		return dbq.User{}, "", ErrLoginTaken // откат вернёт инвайт
	}
	if err != nil {
		return dbq.User{}, "", err
	}
	if err := invite.SetUser(ctx, q, inviteID, user.ID); err != nil {
		return dbq.User{}, "", err
	}
	token, err := session.Create(ctx, q, user.ID, client.UserAgent)
	if err != nil {
		return dbq.User{}, "", err
	}
	if err := tx.Commit(ctx); err != nil {
		return dbq.User{}, "", err
	}
	return user, token, nil
}

// firstErr — первая ошибка в порядке проверок.
func firstErr(errs ...error) error {
	for _, err := range errs {
		if err != nil {
			return err
		}
	}
	return nil
}

// Login проверяет логин (без учёта регистра) и пароль и открывает сессию.
func (s *Service) Login(ctx context.Context, login, password string, client Client) (dbq.User, string, error) {
	loginKey := strings.ToLower(strings.TrimSpace(login))
	if err := s.checkLimits(limitKey{s.ipLimiter, client.IP}, limitKey{s.loginLimiter, loginKey}); err != nil {
		return dbq.User{}, "", err
	}
	fail := func() (dbq.User, string, error) {
		s.ipLimiter.Fail(client.IP)
		s.loginLimiter.Fail(loginKey)
		return dbq.User{}, "", ErrInvalidCredentials
	}

	user, err := s.q.GetUserByLogin(ctx, loginKey)
	if errors.Is(err, pgx.ErrNoRows) {
		auth.VerifyDummy(password) // время ответа — как для существующего логина
		return fail()
	}
	if err != nil {
		return dbq.User{}, "", err
	}
	ok, err := auth.VerifyPassword(password, user.PasswordHash)
	if err != nil {
		return dbq.User{}, "", err
	}
	if !ok {
		return fail()
	}
	s.loginLimiter.Reset(loginKey)

	token, err := session.Create(ctx, s.q, user.ID, client.UserAgent)
	if err != nil {
		return dbq.User{}, "", err
	}
	return user, token, nil
}

// confirmPassword — пароль ещё раз перед изменением учётной записи: украденной сессии мало.
// Неудачи идут в те же лимиты, что и вход, — подбор пароля через сессию их не обходит.
func (s *Service) confirmPassword(user dbq.User, password string, client Client) error {
	loginKey := strings.ToLower(user.Login)
	if err := s.checkLimits(limitKey{s.ipLimiter, client.IP}, limitKey{s.loginLimiter, loginKey}); err != nil {
		return err
	}
	ok, err := auth.VerifyPassword(password, user.PasswordHash)
	if err != nil {
		return err
	}
	if !ok {
		s.ipLimiter.Fail(client.IP)
		s.loginLimiter.Fail(loginKey)
		return ErrWrongPassword
	}
	s.loginLimiter.Reset(loginKey)
	return nil
}

// ChangeLogin меняет логин, если пароль верен. Сессии остаются: логин — не секрет.
// Свой логин можно сменить и только регистром.
func (s *Service) ChangeLogin(ctx context.Context, user dbq.User, newLogin, password string, client Client) (dbq.User, error) {
	newLogin = strings.TrimSpace(newLogin)
	// Формат — до пароля: опечатка в логине не тратит попытки.
	if err := ValidateLogin(newLogin); err != nil {
		return dbq.User{}, err
	}
	if err := s.confirmPassword(user, password, client); err != nil {
		return dbq.User{}, err
	}
	if newLogin == user.Login {
		return user, nil
	}
	updated, err := s.q.UpdateUserLogin(ctx, dbq.UpdateUserLoginParams{ID: user.ID, Login: newLogin})
	if db.IsUniqueViolation(err) {
		return dbq.User{}, ErrLoginTaken
	}
	if err != nil {
		return dbq.User{}, err
	}
	slog.InfoContext(ctx, "login changed", "old_login", user.Login, "new_login", updated.Login)
	return updated, nil
}

// ChangePassword меняет пароль, если текущий верен, и завершает все сессии, кроме keepSession (OWASP):
// если пароль меняют из-за утечки, чужие входы не должны пережить смену.
func (s *Service) ChangePassword(ctx context.Context, user dbq.User, keepSession uuid.UUID, current, newPassword string, client Client) error {
	if err := ValidatePassword(newPassword); err != nil {
		return err
	}
	if err := s.confirmPassword(user, current, client); err != nil {
		return err
	}
	passwordHash := auth.HashPassword(newPassword) // до транзакции: argon2 медленный

	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	q := s.q.WithTx(tx)
	if err := q.UpdateUserPassword(ctx, dbq.UpdateUserPasswordParams{ID: user.ID, PasswordHash: passwordHash}); err != nil {
		return err
	}
	ended, err := session.EndOthers(ctx, q, user.ID, keepSession)
	if err != nil {
		return err
	}
	if err := tx.Commit(ctx); err != nil {
		return err
	}
	slog.InfoContext(ctx, "password changed", "other_sessions_ended", ended)
	return nil
}

// SetAvatar перекодирует картинку (blob.Avatar) и ставит её аватаром. Прежний аватар уберёт очистка.
func (s *Service) SetAvatar(ctx context.Context, user dbq.User, data []byte) (dbq.User, error) {
	avatar, err := blob.Avatar(data) // до транзакции: декодирование и масштабирование — работа процессора
	if err != nil {
		return dbq.User{}, err
	}
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return dbq.User{}, err
	}
	defer tx.Rollback(ctx)
	q := s.q.WithTx(tx)
	sum, err := blob.Put(ctx, q, "image/jpeg", avatar)
	if err != nil {
		return dbq.User{}, err
	}
	updated, err := q.SetUserAvatar(ctx, dbq.SetUserAvatarParams{ID: user.ID, AvatarSha256: sum})
	if err != nil {
		return dbq.User{}, err
	}
	return updated, tx.Commit(ctx)
}

func (s *Service) RemoveAvatar(ctx context.Context, user dbq.User) (dbq.User, error) {
	return s.q.SetUserAvatar(ctx, dbq.SetUserAvatarParams{ID: user.ID})
}

// CreateAdmin — первый администратор (CLI). Без инвайта и без сессии.
func (s *Service) CreateAdmin(ctx context.Context, login, password string) (dbq.User, error) {
	if err := firstErr(ValidateLogin(login), ValidatePassword(password)); err != nil {
		return dbq.User{}, err
	}
	user, err := s.q.CreateUser(ctx, dbq.CreateUserParams{
		Login: login, DisplayName: login, PasswordHash: auth.HashPassword(password), IsAdmin: true,
	})
	if db.IsUniqueViolation(err) {
		return dbq.User{}, ErrLoginTaken
	}
	return user, err
}
