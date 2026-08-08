package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
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
	agencySvc    service.AgencyService
}

func NewIncidentHandler(telemetrySvc service.TelemetryService, dispatchSvc service.DispatchService, agencySvcs ...service.AgencyService) *IncidentHandler {
	var agencySvc service.AgencyService
	if len(agencySvcs) > 0 {
		agencySvc = agencySvcs[0]
	}
	return &IncidentHandler{
		telemetrySvc: telemetrySvc,
		dispatchSvc:  dispatchSvc,
		agencySvc:    agencySvc,
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

	if claims, ok := middleware.GetClaims(r.Context()); ok && claims != nil && claims.Role == domain.RoleCitizen {
		uid := claims.UserID
		filter.CitizenID = &uid
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
	if incidents == nil {
		incidents = []*domain.Incident{}
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

	if !h.authorizeResponderForIncident(w, r, id, nil, nil) {
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
	if history == nil {
		history = []*domain.IncidentStatusHistory{}
	}

	response.JSONResponse(w, http.StatusOK, history)
}

// maxBulkIncidentIDs caps how many incident IDs a single bulk request can
// carry, so a runaway client can't force an unbounded IN (...) query.
const maxBulkIncidentIDs = 200

// parseBulkIncidentIDs reads a comma-separated "incident_ids" query param.
func parseBulkIncidentIDs(w http.ResponseWriter, r *http.Request) ([]int64, bool) {
	raw := r.URL.Query().Get("incident_ids")
	if raw == "" {
		response.JSONError(w, http.StatusBadRequest, "incident_ids is required")
		return nil, false
	}

	parts := strings.Split(raw, ",")
	if len(parts) > maxBulkIncidentIDs {
		response.JSONError(w, http.StatusBadRequest, fmt.Sprintf("too many incident_ids (max %d)", maxBulkIncidentIDs))
		return nil, false
	}

	ids := make([]int64, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		id, err := strconv.ParseInt(p, 10, 64)
		if err != nil {
			response.JSONError(w, http.StatusBadRequest, "invalid incident_ids value: "+p)
			return nil, false
		}
		ids = append(ids, id)
	}
	if len(ids) == 0 {
		response.JSONError(w, http.StatusBadRequest, "incident_ids is required")
		return nil, false
	}
	return ids, true
}

// ListStatusHistoryBulk returns status history for many incidents in one
// request, keyed by incident ID. This exists so views like the reports page
// don't need to call ListStatusHistory once per incident (N+1 requests).
func (h *IncidentHandler) ListStatusHistoryBulk(w http.ResponseWriter, r *http.Request) {
	ids, ok := parseBulkIncidentIDs(w, r)
	if !ok {
		return
	}

	result, err := h.telemetrySvc.ListStatusHistoryBulk(r.Context(), ids)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if result == nil {
		result = map[int64][]*domain.IncidentStatusHistory{}
	}

	response.JSONResponse(w, http.StatusOK, result)
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

	if !h.authorizeResponderForIncident(w, r, incidentID, &req.UnitID, nil) {
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
	if dispatches == nil {
		dispatches = []*domain.IncidentDispatch{}
	}

	response.JSONResponse(w, http.StatusOK, dispatches)
}

// ListDispatchesBulk returns dispatches for many incidents in one request,
// keyed by incident ID. This exists so views like the reports page don't
// need to call ListDispatches once per incident (N+1 requests).
func (h *IncidentHandler) ListDispatchesBulk(w http.ResponseWriter, r *http.Request) {
	ids, ok := parseBulkIncidentIDs(w, r)
	if !ok {
		return
	}

	result, err := h.dispatchSvc.ListDispatchesByIncidentBulk(r.Context(), ids)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if result == nil {
		result = map[int64][]*domain.IncidentDispatch{}
	}

	response.JSONResponse(w, http.StatusOK, result)
}

func (h *IncidentHandler) UpdateDispatchStatus(w http.ResponseWriter, r *http.Request) {
	incidentID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil || incidentID <= 0 {
		response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
		return
	}
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

	if !h.authorizeResponderForIncident(w, r, incidentID, nil, &dispatchID) {
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

func (h *IncidentHandler) authorizeResponderForIncident(w http.ResponseWriter, r *http.Request, incidentID int64, requestedUnitID, dispatchID *int64) bool {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return false
	}
	if claims.Role == domain.RoleAdmin {
		return true
	}
	if claims.Role != domain.RoleResponder || h.agencySvc == nil {
		response.JSONError(w, http.StatusForbidden, "responder access denied")
		return false
	}

	unit, err := h.agencySvc.GetResponderByUserID(r.Context(), claims.UserID)
	if err != nil || unit == nil {
		response.JSONError(w, http.StatusForbidden, "no responder unit assigned to account")
		return false
	}
	if requestedUnitID != nil {
		if unit.ID != *requestedUnitID {
			response.JSONError(w, http.StatusForbidden, "cannot dispatch another responder unit")
			return false
		}
		return true
	}

	dispatches, err := h.dispatchSvc.ListDispatchesByIncident(r.Context(), incidentID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, "failed to verify responder assignment")
		return false
	}
	for _, dispatch := range dispatches {
		if dispatch.UnitID == unit.ID && (dispatchID == nil || dispatch.ID == *dispatchID) {
			return true
		}
	}
	response.JSONError(w, http.StatusForbidden, "incident is not assigned to your responder unit")
	return false
}
