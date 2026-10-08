package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/google/uuid"
)

func characterJSON(id uuid.UUID, name string) []byte {
	return fmt.Appendf(nil, `{"format":"seydlitz.character","formatVersion":1,"id":%q,"system":"dh1","sheetVersion":4,`+
		`"name":%q,"createdAt":"2026-10-08T10:00:00Z","updatedAt":"2026-10-08T10:00:00Z","sheet":{"future":[1,2]}}`, id, name)
}

func (a *testAPI) putCharacter(token string, id uuid.UUID, body []byte, header ...string) *httptest.ResponseRecorder {
	a.t.Helper()
	return a.doRaw(http.MethodPut, "/api/characters/"+id.String(), token, "application/json", body, header...)
}

func characterName(t *testing.T, c characterResponse) string {
	t.Helper()
	var file struct {
		Name string `json:"name"`
	}
	if err := json.Unmarshal(c.Character, &file); err != nil {
		t.Fatalf("character: %v (%s)", err, c.Character)
	}
	return file.Name
}

func TestCharacterWriteAndConflicts(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	id := uuid.New()

	rec := a.putCharacter(token, id, characterJSON(id, "Вэйн"), "If-None-Match", "*")
	expectStatus(t, rec, http.StatusCreated)
	if got := rec.Header().Get("ETag"); got != `"1"` {
		t.Errorf("ETag = %s, want \"1\"", got)
	}
	if c := decode[characterResponse](t, rec); c.Revision != 1 || c.Deleted || c.Character != nil {
		t.Errorf("created = %+v", c)
	}

	// Второе устройство создаёт тот же id — конфликт с версией сервера.
	rec = a.putCharacter(token, id, characterJSON(id, "Другой"), "If-None-Match", "*")
	expectStatus(t, rec, http.StatusPreconditionFailed)
	if c := decode[characterResponse](t, rec); c.Revision != 1 || characterName(t, c) != "Вэйн" {
		t.Errorf("conflict = %+v", c)
	}

	expectStatus(t, a.putCharacter(token, id, characterJSON(id, "Вэйн II"), "If-Match", `"1"`), http.StatusOK)
	rec = a.putCharacter(token, id, characterJSON(id, "Устаревшая"), "If-Match", `"1"`)
	expectStatus(t, rec, http.StatusPreconditionFailed)
	if c := decode[characterResponse](t, rec); c.Revision != 2 || characterName(t, c) != "Вэйн II" {
		t.Errorf("stale write conflict = %+v", c)
	}

	rec = a.do(http.MethodGet, "/api/characters/"+id.String(), token, nil)
	expectStatus(t, rec, http.StatusOK)
	c := decode[characterResponse](t, rec)
	if c.Revision != 2 || characterName(t, c) != "Вэйн II" || rec.Header().Get("ETag") != `"2"` {
		t.Errorf("get = %+v, ETag %s", c, rec.Header().Get("ETag"))
	}
	// Лист сервер не разбирает и хранит как есть.
	if !strings.Contains(string(c.Character), `"future":[1,2]`) {
		t.Errorf("sheet changed: %s", c.Character)
	}
}

func TestCharacterPreconditionsAndValidation(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	id := uuid.New()
	body := characterJSON(id, "Вэйн")

	for _, header := range [][]string{
		nil,
		{"If-Match", "1"},
		{"If-Match", `"0"`},
		{"If-None-Match", `"1"`},
		{"If-Match", `"1"`, "If-None-Match", "*"},
	} {
		rec := a.putCharacter(token, id, body, header...)
		expectStatus(t, rec, http.StatusPreconditionRequired)
		assertJSONError(t, rec, "precondition_required")
	}

	// If-Match, а анкеты нет (база сервера пересоздана) — клиент создаст заново.
	assertJSONError(t, a.putCharacter(token, id, body, "If-Match", `"3"`), "not_found")

	other := uuid.New()
	for _, bad := range [][]byte{
		characterJSON(other, "Чужой id"),
		[]byte(`{"format":"something-else","id":"` + id.String() + `"}`),
		[]byte(`[1]`),
	} {
		rec := a.putCharacter(token, id, bad, "If-None-Match", "*")
		expectStatus(t, rec, http.StatusBadRequest)
		assertJSONError(t, rec, "invalid_character")
	}

	rec := a.putCharacter(token, id, []byte(`{"pad":"`+strings.Repeat("x", 300<<10)+`"}`), "If-None-Match", "*")
	expectStatus(t, rec, http.StatusRequestEntityTooLarge)
	expectStatus(t, a.doRaw(http.MethodPut, "/api/characters/"+id.String(), token, "text/plain", body, "If-None-Match", "*"),
		http.StatusUnsupportedMediaType)
	expectStatus(t, a.do(http.MethodGet, "/api/characters/not-a-uuid", token, nil), http.StatusNotFound)
	expectStatus(t, a.doRaw(http.MethodDelete, "/api/characters/"+id.String(), token, "", nil), http.StatusPreconditionRequired)
	expectStatus(t, a.doRaw(http.MethodDelete, "/api/characters/"+id.String(), token, "", nil, "If-None-Match", "*"),
		http.StatusPreconditionRequired)
}

func TestCharacterBelongsToOwner(t *testing.T) {
	a := newTestAPI(t)
	alice := a.createUser("alice", "alice-password", false)
	bob := a.createUser("bob", "bob-password", false)
	id := uuid.New()
	expectStatus(t, a.putCharacter(alice, id, characterJSON(id, "Вэйн"), "If-None-Match", "*"), http.StatusCreated)

	// Один файл импортировали двое: второму — сохранить под новым id.
	rec := a.putCharacter(bob, id, characterJSON(id, "Вэйн"), "If-None-Match", "*")
	expectStatus(t, rec, http.StatusConflict)
	assertJSONError(t, rec, "id_taken")
	assertJSONError(t, a.putCharacter(bob, id, characterJSON(id, "Вэйн"), "If-Match", `"1"`), "id_taken")
	assertJSONError(t, a.doRaw(http.MethodDelete, "/api/characters/"+id.String(), bob, "", nil, "If-Match", `"1"`), "id_taken")
	expectStatus(t, a.do(http.MethodGet, "/api/characters/"+id.String(), bob, nil), http.StatusNotFound)

	rec = a.do(http.MethodGet, "/api/characters", bob, nil)
	expectStatus(t, rec, http.StatusOK)
	if got := decode[characterChangesResponse](t, rec); len(got.Changes) != 0 {
		t.Errorf("bob sees %+v", got.Changes)
	}
	expectStatus(t, a.do(http.MethodGet, "/api/characters", "", nil), http.StatusUnauthorized)
}

func (a *testAPI) changes(token string, since int64) characterChangesResponse {
	a.t.Helper()
	rec := a.do(http.MethodGet, fmt.Sprintf("/api/characters?since=%d", since), token, nil)
	expectStatus(a.t, rec, http.StatusOK)
	return decode[characterChangesResponse](a.t, rec)
}

func TestCharacterChangesAndTombstones(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	first, second := uuid.New(), uuid.New()
	expectStatus(t, a.putCharacter(token, first, characterJSON(first, "Первая"), "If-None-Match", "*"), http.StatusCreated)
	expectStatus(t, a.putCharacter(token, second, characterJSON(second, "Вторая"), "If-None-Match", "*"), http.StatusCreated)

	all := a.changes(token, 0)
	if all.Cursor != 2 || len(all.Changes) != 2 || all.Changes[0].ID != first || characterName(t, all.Changes[1]) != "Вторая" {
		t.Fatalf("all = %+v", all)
	}
	if got := a.changes(token, all.Cursor); got.Cursor != 2 || len(got.Changes) != 0 {
		t.Errorf("nothing new = %+v", got)
	}

	rec := a.doRaw(http.MethodDelete, "/api/characters/"+first.String(), token, "", nil, "If-Match", `"1"`)
	expectStatus(t, rec, http.StatusOK)
	if c := decode[characterResponse](t, rec); !c.Deleted || c.Revision != 2 {
		t.Errorf("deleted = %+v", c)
	}
	// Повтор удаления (ответ потерялся) — не ошибка и без новой ревизии.
	expectStatus(t, a.doRaw(http.MethodDelete, "/api/characters/"+first.String(), token, "", nil, "If-Match", `"2"`), http.StatusOK)

	got := a.changes(token, all.Cursor)
	if len(got.Changes) != 1 || got.Changes[0].ID != first || !got.Changes[0].Deleted || got.Changes[0].Character != nil {
		t.Errorf("tombstone = %+v", got)
	}
	if fresh := a.changes(token, 0); len(fresh.Changes) != 1 || fresh.Changes[0].ID != second {
		t.Errorf("first sync shows tombstones: %+v", fresh)
	}
	// Курсор из пересозданной базы — всё заново.
	if reset := a.changes(token, 1000); reset.Cursor != got.Cursor || len(reset.Changes) != 1 {
		t.Errorf("reset = %+v", reset)
	}

	// «Оставить мою» после удаления на другом устройстве — запись поверх надгробия.
	expectStatus(t, a.putCharacter(token, first, characterJSON(first, "Вернулась"), "If-Match", `"2"`), http.StatusOK)
	if back := a.changes(token, got.Cursor); len(back.Changes) != 1 || back.Changes[0].Deleted || back.Changes[0].Revision != 3 {
		t.Errorf("restored = %+v", back)
	}

	assertJSONError(t, a.do(http.MethodGet, "/api/characters?since=-1", token, nil), "bad_request")
	assertJSONError(t, a.do(http.MethodGet, "/api/characters?since=x", token, nil), "bad_request")
}

func TestCharacterHistoryIsTrimmed(t *testing.T) {
	a := newTestAPI(t)
	token := a.createUser("player", "player-password", false)
	id := uuid.New()
	expectStatus(t, a.putCharacter(token, id, characterJSON(id, "0"), "If-None-Match", "*"), http.StatusCreated)
	for rev := 1; rev <= 25; rev++ {
		expectStatus(t, a.putCharacter(token, id, characterJSON(id, fmt.Sprint(rev)), "If-Match", fmt.Sprintf(`"%d"`, rev)), http.StatusOK)
	}
	var count, oldest int64
	err := a.pool.QueryRow(context.Background(),
		`SELECT count(*), min(revision) FROM character_revisions WHERE character_id = $1`, id).Scan(&count, &oldest)
	if err != nil {
		t.Fatal(err)
	}
	// Текущая — 26, в истории — 20 прошлых: 6…25.
	if count != 20 || oldest != 6 {
		t.Errorf("history: %d revisions from %d", count, oldest)
	}
}
