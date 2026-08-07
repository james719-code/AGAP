package handler

import (
	"encoding/json"
	"net/http"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type TelemetryHandler struct {
	telemetrySvc service.TelemetryService
}

func NewTelemetryHandler(telemetrySvc service.TelemetryService) *TelemetryHandler {
	return &TelemetryHandler{telemetrySvc: telemetrySvc}
}

type IngestResponse struct {
	Status       string           `json:"status"` // "accepted" | "deduplicated"
	Deduplicated bool             `json:"deduplicated"`
	Incident     *domain.Incident `json:"incident,omitempty"`
	Message      string           `json:"message,omitempty"`
}

type MeshEventsBatchRequest struct {
	Events []*domain.MeshDeliveryEvent `json:"events"`
}

func (h *TelemetryHandler) Uplink(w http.ResponseWriter, r *http.Request) {
	var packet domain.TelemetryPacket
	if err := json.NewDecoder(r.Body).Decode(&packet); err != nil {
		http.Error(w, `{"error":"invalid telemetry JSON payload"}`, http.StatusBadRequest)
		return
	}

	if packet.PacketUUID == "" {
		http.Error(w, `{"error":"packet_uuid is required"}`, http.StatusBadRequest)
		return
	}

	if packet.TargetID.Raw == "" {
		http.Error(w, `{"error":"target_id is required"}`, http.StatusBadRequest)
		return
	}

	// Validate / parse target ID string if domain object isn't fully populated
	if packet.TargetID.Region == "" {
		tid, err := domain.ParseTargetID(packet.TargetID.Raw)
		if err != nil {
			http.Error(w, `{"error":"invalid target_id format"}`, http.StatusBadRequest)
			return
		}
		packet.TargetID = tid
	}

	incident, isDup, err := h.telemetrySvc.IngestPacket(r.Context(), &packet)
	if err != nil {
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	if isDup {
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(IngestResponse{
			Status:       "deduplicated",
			Deduplicated: true,
			Incident:     incident,
			Message:      "Telemetry packet was deduplicated; mesh delivery event recorded.",
		})
		return
	}

	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(IngestResponse{
		Status:       "accepted",
		Deduplicated: false,
		Incident:     incident,
		Message:      "New distress incident created and broadcasted.",
	})
}

func (h *TelemetryHandler) RecordMeshEventsBatch(w http.ResponseWriter, r *http.Request) {
	var req MeshEventsBatchRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid mesh events JSON payload")
		return
	}
	if len(req.Events) == 0 {
		response.JSONError(w, http.StatusBadRequest, "events are required")
		return
	}

	recorded, err := h.telemetrySvc.RecordMeshDeliveryEvents(r.Context(), req.Events)
	if err != nil {
		status := http.StatusInternalServerError
		if err == domain.ErrInvalidMeshDeliveryEvent {
			status = http.StatusBadRequest
		}
		response.JSONError(w, status, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusAccepted, map[string]interface{}{
		"status":   "accepted",
		"recorded": recorded,
	})
}
