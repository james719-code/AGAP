package handler

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/repository"
)

var startTime = time.Now()

type HealthHandler struct {
	telemetryRepo repository.TelemetryRepository
}

func NewHealthHandler(telemetryRepo repository.TelemetryRepository) *HealthHandler {
	return &HealthHandler{telemetryRepo: telemetryRepo}
}

func (h *HealthHandler) Health(w http.ResponseWriter, r *http.Request) {
	dbStatus := "ok"
	if err := h.telemetryRepo.Ping(r.Context()); err != nil {
		dbStatus = "error: " + err.Error()
	}

	w.Header().Set("Content-Type", "application/json")
	if dbStatus != "ok" {
		w.WriteHeader(http.StatusServiceUnavailable)
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status": dbStatus,
		"db":     dbStatus,
		"uptime": time.Since(startTime).String(),
	})
}
