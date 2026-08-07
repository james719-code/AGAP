package handler_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/handler"
)

func TestResponderHandler_RescueUnitMembers(t *testing.T) {
	h := handler.NewResponderHandler(&mockAgencyService{}, nil)
	r := chi.NewRouter()
	r.Post("/rescue-units/{id}/members", h.AddMember)
	r.Get("/rescue-units/{id}/members", h.ListMembers)
	r.Delete("/rescue-units/{id}/members/{responderId}", h.RemoveMember)

	body, _ := json.Marshal(map[string]interface{}{
		"responder_id": 7,
		"is_leader":    true,
	})
	req := httptest.NewRequest(http.MethodPost, "/rescue-units/20/members", bytes.NewBuffer(body))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)
	if rec.Code != http.StatusCreated {
		t.Fatalf("expected 201 Created, got %d: %s", rec.Code, rec.Body.String())
	}

	req = httptest.NewRequest(http.MethodGet, "/rescue-units/20/members", nil)
	rec = httptest.NewRecorder()
	r.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d: %s", rec.Code, rec.Body.String())
	}

	req = httptest.NewRequest(http.MethodDelete, "/rescue-units/20/members/7", nil)
	rec = httptest.NewRecorder()
	r.ServeHTTP(rec, req)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204 No Content, got %d: %s", rec.Code, rec.Body.String())
	}
}
