package blob

import (
	"bytes"
	"errors"
	"image"
	"image/color"
	"image/gif"
	"image/jpeg"
	"image/png"
	"testing"
)

func encodePNG(t *testing.T, img image.Image) []byte {
	t.Helper()
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func filled(w, h int, c color.Color) *image.NRGBA {
	img := image.NewNRGBA(image.Rect(0, 0, w, h))
	for y := range h {
		for x := range w {
			img.Set(x, y, c)
		}
	}
	return img
}

func decodeJPEG(t *testing.T, data []byte) image.Image {
	t.Helper()
	img, err := jpeg.Decode(bytes.NewReader(data))
	if err != nil {
		t.Fatalf("result is not JPEG: %v", err)
	}
	return img
}

func TestAvatarIsSquareJPEG(t *testing.T) {
	for _, size := range []image.Point{{600, 300}, {300, 600}, {100, 100}} {
		var jpg bytes.Buffer
		if err := jpeg.Encode(&jpg, filled(size.X, size.Y, color.White), nil); err != nil {
			t.Fatal(err)
		}
		for name, data := range map[string][]byte{"png": encodePNG(t, filled(size.X, size.Y, color.White)), "jpeg": jpg.Bytes()} {
			out, err := Avatar(data)
			if err != nil {
				t.Fatalf("%s %v: %v", name, size, err)
			}
			if b := decodeJPEG(t, out).Bounds(); b.Dx() != AvatarSide || b.Dy() != AvatarSide {
				t.Errorf("%s %v: result is %v, want %dx%d", name, size, b.Size(), AvatarSide, AvatarSide)
			}
		}
	}
}

func TestAvatarCropsCenter(t *testing.T) {
	// Слева и справа красные поля, в середине зелёный квадрат: в аватар попадает только он.
	img := filled(300, 100, color.RGBA{255, 0, 0, 255})
	for y := range 100 {
		for x := 100; x < 200; x++ {
			img.Set(x, y, color.RGBA{0, 255, 0, 255})
		}
	}
	out, err := Avatar(encodePNG(t, img))
	if err != nil {
		t.Fatal(err)
	}
	result := decodeJPEG(t, out)
	for _, p := range []image.Point{{8, 128}, {128, 128}, {247, 128}} {
		r, g, _, _ := result.At(p.X, p.Y).RGBA()
		if r>>8 > 40 || g>>8 < 200 {
			t.Errorf("pixel %v = r%d g%d, want green", p, r>>8, g>>8)
		}
	}
}

func TestAvatarTransparencyBecomesBackground(t *testing.T) {
	out, err := Avatar(encodePNG(t, filled(64, 64, color.NRGBA{255, 255, 255, 0})))
	if err != nil {
		t.Fatal(err)
	}
	r, g, b, _ := decodeJPEG(t, out).At(128, 128).RGBA()
	if r>>8 > 12 || g>>8 > 14 || b>>8 > 12 {
		t.Errorf("transparent pixel = %d,%d,%d, want screen background %v", r>>8, g>>8, b>>8, background)
	}
}

func TestAvatarRejectsNonImages(t *testing.T) {
	var gifData bytes.Buffer
	if err := gif.Encode(&gifData, filled(10, 10, color.White), nil); err != nil {
		t.Fatal(err)
	}
	truncated := encodePNG(t, filled(50, 50, color.White))
	truncated = truncated[:len(truncated)/2]

	for name, data := range map[string][]byte{
		"text":      []byte("definitely not an image"),
		"empty":     nil,
		"gif":       gifData.Bytes(),
		"truncated": truncated,
	} {
		if _, err := Avatar(data); !errors.Is(err, ErrInvalidImage) {
			t.Errorf("%s: err = %v, want ErrInvalidImage", name, err)
		}
	}
}

func TestAvatarRejectsHugeDimensions(t *testing.T) {
	data := encodePNG(t, image.NewGray(image.Rect(0, 0, maxSide+1, 1)))
	if _, err := Avatar(data); !errors.Is(err, ErrTooLarge) {
		t.Errorf("err = %v, want ErrTooLarge", err)
	}
}
