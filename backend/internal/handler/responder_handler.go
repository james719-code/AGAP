package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type ResponderHandler struct {
	agencySvc   service.AgencyService
	dispatchSvc service.DispatchService
}

func NewResponderHandler(agencySvc service.AgencyService, dispatchSvc service.DispatchService) *ResponderHandler {
	return &ResponderHandler{agencySvc: agencySvc, dispatchSvc: dispatchSvc}
}

func (h *ResponderHandler) Create(w http.ResponseWriter, r *http.Request) {
	var ru domain.ResponderUnit
	if err := json.NewDecoder(r.Body).Decode(&ru); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder unit payload")
		return
	}

	ru.Status = normalizeResponderStatus(ru.Status)
	if !isValidResponderStatus(ru.Status) {
		response.JSONError(w, http.StatusBadRequest, "status must be IDLE, DISPATCHED, or OFFLINE")
		return
	}
	if ru.Status == "DISPATCHED" {
		// New units cannot legitimately be DISPATCHED until an incident assignment exists.
		ru.Status = "IDLE"
	}

	res, err := h.agencySvc.CreateResponder(r.Context(), &ru)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, res)
}

func (h *ResponderHandler) List(w http.ResponseWriter, r *http.Request) {
	var agencyIDPtr *int64
	if agencyIDStr := r.URL.Query().Get("agency_id"); agencyIDStr != "" {
		if aid, err := strconv.ParseInt(agencyIDStr, 10, 64); err == nil {
			agencyIDPtr = &aid
		}
	}

	responders, err := h.agencySvc.ListResponders(r.Context(), agencyIDPtr)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, responders)
}

func (h *ResponderHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder ID")
		return
	}

	responder, err := h.agencySvc.GetResponderByID(r.Context(), id)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "responder not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, responder)
}

func (h *ResponderHandler) GetMyResponderUnit(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	responder, err := h.agencySvc.GetResponderByUserID(r.Context(), claims.UserID)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "no responder unit assigned to user")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, responder)
}

func (h *ResponderHandler) ListMyDispatches(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	responder, err := h.agencySvc.GetResponderByUserID(r.Context(), claims.UserID)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "no responder unit assigned to user")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	dispatches, err := h.dispatchSvc.ListDispatchesByResponderUnit(r.Context(), responder.ID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, dispatches)
}

func (h *ResponderHandler) ListDispatches(w http.ResponseWriter, r *http.Request) {
	id, ok := parseRescueUnitID(w, r)
	if !ok {
		return
	}

	dispatches, err := h.dispatchSvc.ListDispatchesByResponderUnit(r.Context(), id)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, dispatches)
}

type UpdateResponderRequest struct {
	AgencyID       *int64   `json:"agency_id"`
	UserID         *int64   `json:"user_id"`
	TargetID       *string  `json:"target_id"`
	UnitCallsign   *string  `json:"unit_callsign"`
	ResponderName  *string  `json:"responder_name"`
	ResponderPhone *string  `json:"responder_phone"`
	DeviceID       *string  `json:"device_id"`
	Status         *string  `json:"status"`
	CurrentLat     *float64 `json:"current_lat"`
	CurrentLon     *float64 `json:"current_lon"`
}

func (h *ResponderHandler) Update(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder ID")
		return
	}

	current, err := h.agencySvc.GetResponderByID(r.Context(), id)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "responder not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	var req UpdateResponderRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}

	// PUT is treated as a safe merge for mobile/web clients. Older Android builds
	// only send the editable fields; preserving the rest prevents user/device/GPS
	// data from being zeroed every time command edits a callsign or status.
	if req.AgencyID != nil {
		current.AgencyID = *req.AgencyID
	}
	if req.UserID != nil {
		current.UserID = req.UserID
	}
	if req.TargetID != nil {
		current.TargetID = strings.TrimSpace(*req.TargetID)
	}
	if req.UnitCallsign != nil {
		current.UnitCallsign = strings.TrimSpace(*req.UnitCallsign)
	}
	if req.ResponderName != nil {
		current.ResponderName = strings.TrimSpace(*req.ResponderName)
	}
	if req.ResponderPhone != nil {
		current.ResponderPhone = strings.TrimSpace(*req.ResponderPhone)
	}
	if req.DeviceID != nil {
		current.DeviceID = strings.TrimSpace(*req.DeviceID)
	}
	if req.CurrentLat != nil {
		current.CurrentLat = *req.CurrentLat
	}
	if req.CurrentLon != nil {
		current.CurrentLon = *req.CurrentLon
	}
	if req.Status != nil {
		current.Status = normalizeResponderStatus(*req.Status)
	}

	if !isValidResponderStatus(current.Status) {
		response.JSONError(w, http.StatusBadRequest, "status must be IDLE, DISPATCHED, or OFFLINE")
		return
	}
	active, activeErr := h.dispatchSvc.GetActiveDispatchByResponderUnit(r.Context(), id)
	if activeErr != nil && activeErr != domain.ErrNotFound {
		response.JSONError(w, http.StatusInternalServerError, activeErr.Error())
		return
	}
	if current.Status == "IDLE" && active != nil {
		response.JSONError(w, http.StatusConflict, "unit is assigned to an active incident and cannot be set to IDLE")
		return
	}
	if current.Status == "DISPATCHED" && active == nil {
		response.JSONError(w, http.StatusConflict, "DISPATCHED is managed by an active incident assignment")
		return
	}

	res, err := h.agencySvc.UpdateResponder(r.Context(), current)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "responder not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, res)
}

type UpdateResponderLocationRequest struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Status    string  `json:"status"`
}

func (h *ResponderHandler) UpdateLocation(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder ID")
		return
	}

	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	if claims.Role == domain.RoleResponder {
		unit, err := h.agencySvc.GetResponderByUserID(r.Context(), claims.UserID)
		if err != nil || unit == nil || unit.ID != id {
			response.JSONError(w, http.StatusForbidden, "cannot update another responder unit")
			return
		}
	}

	var req UpdateResponderLocationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}
	req.Status = normalizeResponderStatus(req.Status)
	if !isValidResponderStatus(req.Status) {
		response.JSONError(w, http.StatusBadRequest, "status must be IDLE, DISPATCHED, or OFFLINE")
		return
	}
	active, activeErr := h.dispatchSvc.GetActiveDispatchByResponderUnit(r.Context(), id)
	if activeErr != nil && activeErr != domain.ErrNotFound {
		response.JSONError(w, http.StatusInternalServerError, activeErr.Error())
		return
	}
	if req.Status == "IDLE" && active != nil {
		response.JSONError(w, http.StatusConflict, "unit is assigned to an active incident and cannot be set to IDLE")
		return
	}
	if req.Status == "DISPATCHED" && active == nil {
		response.JSONError(w, http.StatusConflict, "DISPATCHED is managed by an active incident assignment")
		return
	}

	if err := h.agencySvc.UpdateResponderLocation(r.Context(), id, req.Latitude, req.Longitude, req.Status); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "responder not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"status":       "success",
		"responder_id": id,
	})
}

func normalizeResponderStatus(status string) string {
	status = strings.ToUpper(strings.TrimSpace(status))
	if status == "" || status == "NONE" || status == "NULL" {
		return "IDLE"
	}
	return status
}

func isValidResponderStatus(status string) bool {
	switch status {
	case "IDLE", "DISPATCHED", "OFFLINE":
		return true
	default:
		return false
	}
}

func (h *ResponderHandler) Delete(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder ID")
		return
	}

	if err := h.agencySvc.DeleteResponder(r.Context(), id); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "responder not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

type AddRescueUnitMemberRequest struct {
	ResponderID int64 `json:"responder_id"`
	IsLeader    bool  `json:"is_leader"`
}

func (h *ResponderHandler) AddMember(w http.ResponseWriter, r *http.Request) {
	rescueUnitID, ok := parseRescueUnitID(w, r)
	if !ok {
		return
	}

	var req AddRescueUnitMemberRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid rescue unit member payload")
		return
	}

	member, err := h.agencySvc.AddRescueUnitMember(r.Context(), &domain.RescueUnitMember{
		RescueUnitID: rescueUnitID,
		ResponderID:  req.ResponderID,
		IsLeader:     req.IsLeader,
	})
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, member)
}

func (h *ResponderHandler) ListMembers(w http.ResponseWriter, r *http.Request) {
	rescueUnitID, ok := parseRescueUnitID(w, r)
	if !ok {
		return
	}

	members, err := h.agencySvc.ListRescueUnitMembers(r.Context(), rescueUnitID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, members)
}

func (h *ResponderHandler) RemoveMember(w http.ResponseWriter, r *http.Request) {
	rescueUnitID, ok := parseRescueUnitID(w, r)
	if !ok {
		return
	}
	responderID, err := strconv.ParseInt(chi.URLParam(r, "responderId"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder ID")
		return
	}

	if err := h.agencySvc.RemoveRescueUnitMember(r.Context(), rescueUnitID, responderID); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "rescue unit member not found")
			return
		}
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func parseRescueUnitID(w http.ResponseWriter, r *http.Request) (int64, bool) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid rescue unit ID")
		return 0, false
	}
	return id, true
}
