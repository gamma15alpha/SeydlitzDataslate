package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"strings"
)

// NewSecret возвращает случайный секрет (128 бит, base32 — удобно копировать и диктовать)
// и его хеш для хранения в базе. Сам секрет сервер не хранит.
func NewSecret() (secret string, hash []byte) {
	secret = rand.Text()
	return secret, HashSecret(secret)
}

// HashSecret — хеш секрета для поиска в базе. Регистр и пробелы по краям не важны:
// коды инвайтов люди вводят руками.
func HashSecret(secret string) []byte {
	sum := sha256.Sum256([]byte(strings.ToUpper(strings.TrimSpace(secret))))
	return sum[:]
}
