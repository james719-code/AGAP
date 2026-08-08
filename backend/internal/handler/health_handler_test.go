package handler_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/config"
	"github.com/travelingsalesman/agap/backend/internal/handler"
	"github.com/travelingsalesman/agap/backend/internal/platform/database"
)

func TestHealthAndInfoHandlers(t *testing.T) {
	cfg := &config.Config{
		Host:            "0.0.0.0",
		Port:            "8080",
		Env:             "test",
		DBType:          "sqlite",
		DBFilePath:      ":memory:",
		RateLimitRPS:    100,
		RateLimitBurst:  200,
	}

	container, err := database.NewContainer(cfg)
	if err != nil {
		t.Fatalf("failed to create database container: %v", err)
	}
	defer container.DB.Close()

	h := handler.NewHealthHandler(container.TelemetryRepo, cfg, container.DB)

	t.Run("Health Probe Endpoint", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/health", nil)
		rec := httptest.NewRecorder()

		h.Health(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 OK, got %d", rec.Code)
		}

		var body map[string]interface{}
		if err := json.NewDecoder(rec.Body).Decode(&body); err != nil {
			t.Fatalf("failed to decode health response: %v", err)
		}

		if body["status"] != "ok" || body["db"] != "ok" {
			t.Errorf("unexpected body content: %+v", body)
		}
	})

	t.Run("System Info Endpoint", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/info", nil)
		rec := httptest.NewRecorder()

		h.Info(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 OK, got %d", rec.Code)
		}

		var info handler.SystemInfoResponse
		if err := json.NewDecoder(rec.Body).Decode(&info); err != nil {
			t.Fatalf("failed to decode system info response: %v", err)
		}

		if info.Status != "ok" {
			t.Errorf("expected overall status ok, got %q", info.Status)
		}
		if info.API.Name == "" || info.API.Version == "" {
			t.Errorf("expected API name and version to be populated, got %+v", info.API)
		}
		if info.Database.Type != "sqlite" || info.Database.Status != "ok" {
			t.Errorf("unexpected database info: %+v", info.Database)
		}
		if _, exists := info.Database.Statistics["users"]; !exists {
			t.Errorf("expected users count in statistics, got %+v", info.Database.Statistics)
		}
	})

	t.Run("Dashboard UI Endpoint", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/info", nil)
		rec := httptest.NewRecorder()

		h.DashboardUI(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 OK, got %d", rec.Code)
		}
		if contentType := rec.Header().Get("Content-Type"); contentType != "text/html; charset=utf-8" {
			t.Errorf("expected text/html content-type, got %s", contentType)
		}
		if body := rec.Body.String(); len(body) == 0 || !testing.Short() && (len(body) < 100) {
			t.Errorf("expected non-empty HTML body")
		}
	})

	t.Run("List DB Tables Endpoint", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/system/db/tables", nil)
		rec := httptest.NewRecorder()

		h.ListTables(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 OK, got %d", rec.Code)
		}

		var resp map[string]interface{}
		if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
			t.Fatalf("failed to decode list tables response: %v", err)
		}
		if _, ok := resp["tables"]; !ok {
			t.Errorf("expected tables array in response: %+v", resp)
		}
	})

	t.Run("Get DB Table Data Endpoint", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/system/db/tables?table=users", nil)
		rec := httptest.NewRecorder()

		h.GetTableData(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 OK, got %d", rec.Code)
		}

		var resp map[string]interface{}
		if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
			t.Fatalf("failed to decode table data response: %v", err)
		}
		if resp["tableName"] != "users" {
			t.Errorf("expected tableName users, got %v", resp["tableName"])
		}
	})
}
