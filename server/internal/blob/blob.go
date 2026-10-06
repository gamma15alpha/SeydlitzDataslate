// Package blob — картинки по SHA-256 содержимого (D63): перекодирование присланного, хранение, выдача, очистка.
// Функции принимают *dbq.Queries — их можно звать и внутри чужой транзакции.
package blob

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"image"
	"image/color"
	"image/jpeg"
	_ "image/png"
	"time"

	"github.com/jackc/pgx/v5"
	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/db/dbq"
)

const (
	MaxUploadBytes = 5 << 20
	// Защита от «бомб»: маленький файл с огромными размерами съел бы память при декодировании.
	maxSide     = 8000
	AvatarSide  = 256
	jpegQuality = 85
	// Запас между загрузкой картинки и записью, которая на неё сошлётся (портрет → синхронизация анкеты).
	KeepUnused = 24 * time.Hour
)

var (
	ErrInvalidImage = errors.New("not a PNG, JPEG or WebP image")
	ErrTooLarge     = errors.New("image is too large")
	ErrNotFound     = errors.New("image not found")
)

// Прозрачное — на фон экрана: в JPEG нет альфы.
var background = color.RGBA{R: 3, G: 6, B: 3, A: 255}

// Avatar — квадрат AvatarSide из середины картинки, заново в JPEG: без EXIF и без того, что было в файле кроме пикселей.
func Avatar(data []byte) ([]byte, error) {
	src, err := decode(data)
	if err != nil {
		return nil, err
	}
	b := src.Bounds()
	side := min(b.Dx(), b.Dy())
	crop := image.Rect(0, 0, side, side).Add(b.Min).Add(image.Pt((b.Dx()-side)/2, (b.Dy()-side)/2))

	dst := image.NewRGBA(image.Rect(0, 0, AvatarSide, AvatarSide))
	draw.Draw(dst, dst.Bounds(), image.NewUniform(background), image.Point{}, draw.Src)
	draw.CatmullRom.Scale(dst, dst.Bounds(), src, crop, draw.Over, nil)

	var out bytes.Buffer
	if err := jpeg.Encode(&out, dst, &jpeg.Options{Quality: jpegQuality}); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

func decode(data []byte) (image.Image, error) {
	cfg, format, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil || !allowedFormat(format) {
		return nil, ErrInvalidImage
	}
	if cfg.Width > maxSide || cfg.Height > maxSide {
		return nil, ErrTooLarge
	}
	if cfg.Width == 0 || cfg.Height == 0 {
		return nil, ErrInvalidImage
	}
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, ErrInvalidImage
	}
	return img, nil
}

func allowedFormat(format string) bool {
	return format == "png" || format == "jpeg" || format == "webp"
}

// Put сохраняет картинку и возвращает её SHA-256.
func Put(ctx context.Context, q *dbq.Queries, mime string, data []byte) ([]byte, error) {
	sum := sha256.Sum256(data)
	if err := q.PutBlob(ctx, dbq.PutBlobParams{Sha256: sum[:], Mime: mime, Bytes: data}); err != nil {
		return nil, err
	}
	return sum[:], nil
}

func Get(ctx context.Context, q *dbq.Queries, sum []byte) (dbq.GetBlobRow, error) {
	row, err := q.GetBlob(ctx, sum)
	if errors.Is(err, pgx.ErrNoRows) {
		return dbq.GetBlobRow{}, ErrNotFound
	}
	return row, err
}

// Cleanup удаляет картинки без ссылок, пролежавшие дольше KeepUnused.
func Cleanup(ctx context.Context, q *dbq.Queries, now time.Time) (int64, error) {
	return q.DeleteUnusedBlobs(ctx, now.Add(-KeepUnused))
}
