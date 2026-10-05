package api

import (
	"net/http"
	"os"
	"slices"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"go.yaml.in/yaml/v3"
)

const specPath = "../../../schemas/openapi.yaml"

// TestRoutesMatchSpec — маршруты сервера и schemas/openapi.yaml должны совпадать
// в обе стороны: ни недокументированных маршрутов, ни описанных, но не реализованных.
func TestRoutesMatchSpec(t *testing.T) {
	raw, err := os.ReadFile(specPath)
	if err != nil {
		t.Fatalf("read spec: %v", err)
	}
	var spec struct {
		OpenAPI string                    `yaml:"openapi"`
		Paths   map[string]map[string]any `yaml:"paths"`
	}
	if err := yaml.Unmarshal(raw, &spec); err != nil {
		t.Fatalf("parse spec: %v", err)
	}
	if !strings.HasPrefix(spec.OpenAPI, "3.1.") {
		t.Errorf("openapi = %q, want 3.1.x", spec.OpenAPI)
	}

	var inSpec []string
	for path, item := range spec.Paths {
		for method := range item {
			if slices.Contains(methods, strings.ToUpper(method)) {
				inSpec = append(inSpec, strings.ToUpper(method)+" "+path)
			}
		}
	}

	var inRouter []string
	walk := func(method, route string, _ http.Handler, _ ...func(http.Handler) http.Handler) error {
		inRouter = append(inRouter, method+" "+route)
		return nil
	}
	if err := chi.Walk(NewHandler(Config{}).(*chi.Mux), walk); err != nil {
		t.Fatalf("walk routes: %v", err)
	}

	for _, r := range inRouter {
		if !slices.Contains(inSpec, r) {
			t.Errorf("route %s is not described in %s", r, specPath)
		}
	}
	for _, r := range inSpec {
		if !slices.Contains(inRouter, r) {
			t.Errorf("%s describes %s, but the server has no such route", specPath, r)
		}
	}
}
