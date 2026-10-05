// Package auth — пароли, секреты (токены сессий, коды инвайтов) и ограничение попыток.
package auth

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"
	"sync"
	"unicode/utf8"

	"golang.org/x/crypto/argon2"
)

// Параметры argon2id — рекомендация OWASP (19 МиБ, 2 прохода, 1 поток).
// Хранятся в самом хеше, так что их можно менять: старые хеши проверяются со своими.
const (
	argonMemory  = 19 * 1024 // КиБ
	argonTime    = 2
	argonThreads = 1
	argonKeyLen  = 32
	saltLen      = 16
)

var b64 = base64.RawStdEncoding

// HashPassword возвращает хеш argon2id в PHC-формате: $argon2id$v=19$m=…,t=…,p=…$соль$хеш.
func HashPassword(password string) string {
	salt := make([]byte, saltLen)
	rand.Read(salt)
	key := argon2.IDKey([]byte(password), salt, argonTime, argonMemory, argonThreads, argonKeyLen)
	return fmt.Sprintf("$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
		argon2.Version, argonMemory, argonTime, argonThreads, b64.EncodeToString(salt), b64.EncodeToString(key))
}

// VerifyPassword сравнивает пароль с хешем из HashPassword за постоянное время.
func VerifyPassword(password, encoded string) (bool, error) {
	parts := strings.Split(encoded, "$")
	if len(parts) != 6 || parts[1] != "argon2id" {
		return false, errors.New("unsupported password hash format")
	}
	var version int
	if _, err := fmt.Sscanf(parts[2], "v=%d", &version); err != nil || version != argon2.Version {
		return false, errors.New("unsupported argon2 version")
	}
	var memory, time uint32
	var threads uint8
	if _, err := fmt.Sscanf(parts[3], "m=%d,t=%d,p=%d", &memory, &time, &threads); err != nil {
		return false, fmt.Errorf("argon2 params: %w", err)
	}
	salt, err := b64.DecodeString(parts[4])
	if err != nil {
		return false, fmt.Errorf("argon2 salt: %w", err)
	}
	want, err := b64.DecodeString(parts[5])
	if err != nil {
		return false, fmt.Errorf("argon2 key: %w", err)
	}
	got := argon2.IDKey([]byte(password), salt, time, memory, threads, uint32(len(want)))
	return subtle.ConstantTimeCompare(got, want) == 1, nil
}

var dummyHash = sync.OnceValue(func() string { return HashPassword("dummy password") })

// VerifyDummy тратит столько же времени, сколько VerifyPassword: вход с несуществующим
// логином не должен отвечать заметно быстрее, иначе по времени можно перебирать логины.
func VerifyDummy(password string) {
	_, _ = VerifyPassword(password, dummyHash())
}

// ValidateLogin возвращает описание проблемы или "" для допустимого логина.
func ValidateLogin(login string) string {
	if len(login) < 3 || len(login) > 32 {
		return "login must be 3 to 32 characters long"
	}
	for _, c := range login {
		if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-') {
			return "login may contain only latin letters, digits, '_', '.' and '-'"
		}
	}
	return ""
}

// ValidatePassword возвращает описание проблемы или "" для допустимого пароля.
func ValidatePassword(password string) string {
	if n := utf8.RuneCountInString(password); n < 8 || n > 128 {
		return "password must be 8 to 128 characters long"
	}
	return ""
}
