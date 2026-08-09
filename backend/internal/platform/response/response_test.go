package response

import (
	"net/http/httptest"
	"strings"
	"testing"
)

func TestJSONResponse_NilSlice(t *testing.T) {
	w := httptest.NewRecorder()
	var nilSlice []string
	JSONResponse(w, 200, nilSlice)

	body := strings.TrimSpace(w.Body.String())
	if body != "[]" {
		t.Errorf("expected '[]', got %q", body)
	}
}

func TestJSONResponse_NilSliceInMap(t *testing.T) {
	w := httptest.NewRecorder()
	var nilSlice []string
	m := map[string]interface{}{
		"total": 0,
		"items": nilSlice,
	}
	JSONResponse(w, 200, m)

	body := strings.TrimSpace(w.Body.String())
	if !strings.Contains(body, `"items":[]`) {
		t.Errorf("expected 'items' to be '[]', got %q", body)
	}
}
