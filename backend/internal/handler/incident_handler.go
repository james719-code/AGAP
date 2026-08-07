package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type IncidentHandler struct {
	telemetrySvc service.TelemetryService
	dispatchSvc  service.DispatchService
}

func NewIncidentHandler(telemetrySvc service.TelemetryService, dispatchSvc service.DispatchService) *IncidentHandler {
	return &IncidentHandler{
		telemetrySvc: telemetrySvc,
		dispatchSvc:  dispatchSvc,
	}
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

	incidents, total, err := h.telemetrySvc.ListIncidents(r.Context(), filter)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"total":     total,
		"count":     len(incidents),
		"limit":     filter.Limit,
		"offset":    filter.Offset,
		"incidents": incidents,
	})
}

func (h *IncidentHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
		return
	}

	inc, err := h.telemetrySvc.GetIncidentByID(r.Context(), id)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "incident not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, inc)
}

type UpdateStatusRequest struct {
	Status domain.IncidentStatus `json:"status"`
	Notes  string                `json:"notes,omitempty"`
}

func (h *IncidentHandler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
		return
	}

	var req UpdateStatusRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}

	var changedBy *int64
	if claims, ok := middleware.GetClaims(r.Context()); ok && claims != nil {
		changedBy = &claims.UserID
	}

	if err := h.telemetrySvc.UpdateStatus(r.Context(), id, req.Status, changedBy, req.Notes); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "incident not found")
			return
		}
		if err == domain.ErrInvalidStatusTransition {
			response.JSONError(w, http.StatusBadRequest, "invalid incident status transition")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"status":      "success",
		"incident_id": id,
		"new_status":  req.Status,
	})
}

func (h *IncidentHandler) ListStatusHistory(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	incidentID, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
		return
	}

	history, err := h.telemetrySvc.ListStatusHistory(r.Context(), incidentID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, history)
}

func (h *IncidentHandler) Dispatch(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	incidentID, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
		return
	}

	var req domain.CreateDispatchRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.UnitID == 0 {
		response.JSONError(w, http.StatusBadRequest, "unit_id is required")
		return
	}

	var dispatchedBy *int64
	if claims, ok := middleware.GetClaims(r.Context()); ok && claims != nil {
		dispatchedBy = &claims.UserID
	}

	dispatch, err := h.dispatchSvc.CreateDispatch(r.Context(), incidentID, req.UnitID, dispatchedBy)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, dispatch)
}

func (h *IncidentHandler) ListDispatches(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	incidentID, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
		return
	}

	dispatches, err := h.dispatchSvc.ListDispatchesByIncident(r.Context(), incidentID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, dispatches)
}

func (h *IncidentHandler) UpdateDispatchStatus(w http.ResponseWriter, r *http.Request) {
	dispatchIDStr := chi.URLParam(r, "dispatchID")
	dispatchID, err := strconv.ParseInt(dispatchIDStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid dispatch ID")
		return
	}

	var req domain.UpdateDispatchStatusRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Status == "" {
		response.JSONError(w, http.StatusBadRequest, "status is required")
		return
	}

	var changedBy *int64
	if claims, ok := middleware.GetClaims(r.Context()); ok && claims != nil {
		changedBy = &claims.UserID
	}

	if err := h.dispatchSvc.UpdateDispatchStatus(r.Context(), dispatchID, req.Status, changedBy); err != nil {
		switch err {
		case domain.ErrNotFound:
			response.JSONError(w, http.StatusNotFound, "dispatch not found")
		case domain.ErrInvalidDispatchTransition:
			response.JSONError(w, http.StatusBadRequest, "invalid dispatch status transition")
		default:
			response.JSONError(w, http.StatusInternalServerError, err.Error())
		}
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"status":      "success",
		"dispatch_id": dispatchID,
		"new_status":  req.Status,
	})
}
