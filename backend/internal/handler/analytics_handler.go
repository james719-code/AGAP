package handler

import (
	"net/http"

	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type AnalyticsHandler struct{ service service.AnalyticsService }

func NewAnalyticsHandler(service service.AnalyticsService) *AnalyticsHandler {
	return &AnalyticsHandler{service: service}
}

func (h *AnalyticsHandler) TelemetryInsights(w http.ResponseWriter, r *http.Request) {
	insights, err := h.service.GetTelemetryInsights(r.Context())
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, "failed to calculate telemetry insights")
		return
	}
	response.JSONResponse(w, http.StatusOK, insights)
}
