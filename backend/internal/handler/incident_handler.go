package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type IncidentHandler struct {
	telemetrySvc service.TelemetryService
}

func NewIncidentHandler(telemetrySvc service.TelemetryService) *IncidentHandler {
	return &IncidentHandler{telemetrySvc: telemetrySvc}
}

func (h *IncidentHandler) List(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()

	limit, _ := strconv.Atoi(q.Get("limit"))
	offset, _ := strconv.Atoi(q.Get("offset"))

	filter := repository.IncidentFilter{
		IncidentType: q.Get("type"),
		Status:       q.Get("status"),
		RegionCode:   q.Get("region"),
		Limit:        limit,
		Offset:       offset,
	}

	if sinceStr := q.Get("since"); sinceStr != "" {
		if t, err := time.Parse(time.RFC3339, sinceStr); err == nil {
			filter.Since = &t
		}
	}

	incidents, err := h.telemetrySvc.ListIncidents(r.Context(), filter)
	if err != nil {
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"count":     len(incidents),
		"limit":     filter.Limit,
		"offset":    filter.Offset,
		"incidents": incidents,
	})
}

type UpdateStatusRequest struct {
	Status domain.IncidentStatus `json:"status"`
}

func (h *IncidentHandler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, `{"error":"invalid incident ID"}`, http.StatusBadRequest)
		return
	}

	var req UpdateStatusRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid payload"}`, http.StatusBadRequest)
		return
	}

	if err := h.telemetrySvc.UpdateStatus(r.Context(), id, req.Status); err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"incident not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":      "success",
		"incident_id": id,
		"new_status":  req.Status,
	})
}
