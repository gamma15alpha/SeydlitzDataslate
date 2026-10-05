package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"strings"
)

// NewSecret возвращает 128-битный секрет в base32 и его хеш; хранится только хеш.
func NewSecret() (secret string, hash []byte) {
	secret = rand.Text()
	return secret, HashSecret(secret)
}

// HashSecret нечувствителен к регистру и пробелам: коды вводят руками.
func HashSecret(secret string) []byte {
	sum := sha256.Sum256([]byte(strings.ToUpper(strings.TrimSpace(secret))))
	return sum[:]
}
