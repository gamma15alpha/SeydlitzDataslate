package logging

import (
	"bytes"
	"context"
	"encoding/json"
	"testing"
)

func TestRequestAttrsFromContext(t *testing.T) {
	var buf bytes.Buffer
	log, err := New(&buf, "json", "info")
	if err != nil {
		t.Fatal(err)
	}
	info := &RequestInfo{ID: "req-1"}
	ctx := WithRequest(context.Background(), info)

	log.InfoContext(ctx, "before auth")
	info.UserID = "user-1"
	log.With("component", "x").InfoContext(ctx, "after auth")
	log.Info("no request")

	var records []map[string]any
	for line := range bytes.Lines(buf.Bytes()) {
		var rec map[string]any
		if err := json.Unmarshal(line, &rec); err != nil {
			t.Fatal(err)
		}
		records = append(records, rec)
	}
	if len(records) != 3 {
		t.Fatalf("got %d records, want 3", len(records))
	}
	if records[0]["request_id"] != "req-1" || records[0]["user_id"] != nil {
		t.Errorf("before auth: %v", records[0])
	}
	if records[1]["request_id"] != "req-1" || records[1]["user_id"] != "user-1" || records[1]["component"] != "x" {
		t.Errorf("after auth: %v", records[1])
	}
	if records[2]["request_id"] != nil {
		t.Errorf("without request: %v", records[2])
	}
}

func TestNewRejectsBadConfig(t *testing.T) {
	if _, err := New(nil, "xml", "info"); err == nil {
		t.Error("unknown format accepted")
	}
	if _, err := New(nil, "text", "verbose"); err == nil {
		t.Error("unknown level accepted")
	}
}
