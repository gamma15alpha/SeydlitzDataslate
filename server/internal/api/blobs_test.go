package api

import (
	"bytes"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gamma15alpha/SeydlitzDataslate/server/internal/blob"
)

func (a *testAPI) doRaw(method, path, token, contentType string, body []byte, header ...string) *httptest.ResponseRecorder {
	a.t.Helper()
	req := httptest.NewRequest(method, path, bytes.NewReader(body))
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	for i := 0; i+1 < len(header); i += 2 {
		req.Header.Set(header[i], header[i+1])
	}
	rec := httptest.NewRecorder()
	a.h.ServeHTTP(rec, req)
	return rec
}

func testPNG(t *testing.T) []byte {
	t.Helper()
	img := image.NewNRGBA(image.Rect(0, 0, 400, 300))
	for i := range img.Pix {
		img.Pix[i] = 200
	}
	img.Set(0, 0, color.Black)
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func TestAvatar(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)

	me := decode[userResponse](t, a.do(http.MethodGet, "/api/me", token, nil))
	if me.Avatar != nil {
		t.Fatalf("new user has avatar %q", *me.Avatar)
	}

	rec := a.doRaw(http.MethodPut, "/api/me/avatar", token, "image/png", testPNG(t))
	expectStatus(t, rec, http.StatusOK)
	user := decode[userResponse](t, rec)
	if user.Avatar == nil || len(*user.Avatar) != 64 {
		t.Fatalf("avatar = %v, want 64 hex chars", user.Avatar)
	}
	path := "/api/blobs/" + *user.Avatar

	rec = a.doRaw(http.MethodGet, path, token, "", nil)
	expectStatus(t, rec, http.StatusOK)
	if ct := rec.Header().Get("Content-Type"); ct != "image/jpeg" {
		t.Errorf("Content-Type = %q, want image/jpeg", ct)
	}
	if cc := rec.Header().Get("Cache-Control"); !strings.Contains(cc, "immutable") || !strings.Contains(cc, "private") {
		t.Errorf("Cache-Control = %q, want private immutable", cc)
	}
	img, err := jpeg.Decode(rec.Body)
	if err != nil {
		t.Fatalf("decode avatar: %v", err)
	}
	if b := img.Bounds(); b.Dx() != blob.AvatarSide || b.Dy() != blob.AvatarSide {
		t.Errorf("avatar is %v, want %dx%d", b.Size(), blob.AvatarSide, blob.AvatarSide)
	}

	rec = a.doRaw(http.MethodGet, path, token, "", nil, "If-None-Match", `"`+*user.Avatar+`"`)
	expectStatus(t, rec, http.StatusNotModified)

	me = decode[userResponse](t, a.do(http.MethodGet, "/api/me", token, nil))
	if me.Avatar == nil || *me.Avatar != *user.Avatar {
		t.Errorf("/api/me avatar = %v, want %s", me.Avatar, *user.Avatar)
	}

	rec = a.do(http.MethodDelete, "/api/me/avatar", token, nil)
	expectStatus(t, rec, http.StatusOK)
	if removed := decode[userResponse](t, rec); removed.Avatar != nil {
		t.Errorf("avatar after delete = %q, want null", *removed.Avatar)
	}
}

func TestAvatarRejectsBadUploads(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)

	rec := a.doRaw(http.MethodPut, "/api/me/avatar", token, "text/plain", testPNG(t))
	expectStatus(t, rec, http.StatusUnsupportedMediaType)
	assertJSONError(t, rec, "unsupported_media_type")

	rec = a.doRaw(http.MethodPut, "/api/me/avatar", token, "image/png", []byte("not a png"))
	expectStatus(t, rec, http.StatusBadRequest)
	assertJSONError(t, rec, "invalid_image")

	rec = a.doRaw(http.MethodPut, "/api/me/avatar", token, "image/png", make([]byte, blob.MaxUploadBytes+1))
	expectStatus(t, rec, http.StatusRequestEntityTooLarge)
	assertJSONError(t, rec, "too_large")

	rec = a.doRaw(http.MethodPut, "/api/me/avatar", "", "image/png", testPNG(t))
	expectStatus(t, rec, http.StatusUnauthorized)
}

func TestGetBlobErrors(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	unknown := strings.Repeat("ab", 32)

	for _, path := range []string{"/api/blobs/" + unknown, "/api/blobs/zz", "/api/blobs/" + strings.ToUpper(unknown)} {
		rec := a.doRaw(http.MethodGet, path, token, "", nil)
		expectStatus(t, rec, http.StatusNotFound)
		assertJSONError(t, rec, "not_found")
		if cc := rec.Header().Get("Cache-Control"); cc != "" {
			t.Errorf("%s: error cached: Cache-Control = %q", path, cc)
		}
	}

	rec := a.doRaw(http.MethodGet, "/api/blobs/"+unknown, "", "", nil)
	expectStatus(t, rec, http.StatusUnauthorized)
}
