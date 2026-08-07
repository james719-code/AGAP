package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type EmergencyTypeHandler struct {
	emergencyTypeSvc service.EmergencyTypeService
}

func NewEmergencyTypeHandler(emergencyTypeSvc service.EmergencyTypeService) *EmergencyTypeHandler {
	return &EmergencyTypeHandler{emergencyTypeSvc: emergencyTypeSvc}
}

func (h *EmergencyTypeHandler) List(w http.ResponseWriter, r *http.Request) {
	includeInactive := r.URL.Query().Get("include_inactive") == "true"
	if includeInactive {
		claims, ok := middleware.GetClaims(r.Context())
		if !ok || claims == nil || claims.Role != domain.RoleAdmin {
			response.JSONError(w, http.StatusForbidden, "inactive emergency types require admin access")
			return
		}
	}

	emergencyTypes, err := h.emergencyTypeSvc.ListEmergencyTypes(r.Context(), includeInactive)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	response.JSONResponse(w, http.StatusOK, emergencyTypes)
}

func (h *EmergencyTypeHandler) Create(w http.ResponseWriter, r *http.Request) {
	emergencyType, ok := decodeEmergencyTypePayload(w, r)
	if !ok {
		return
	}
	if !emergencyType.IsActive {
		emergencyType.IsActive = true
	}

	created, err := h.emergencyTypeSvc.CreateEmergencyType(r.Context(), emergencyType)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}
	response.JSONResponse(w, http.StatusCreated, created)
}

func (h *EmergencyTypeHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid emergency type ID")
		return
	}

	emergencyType, ok := decodeEmergencyTypePayload(w, r)
	if !ok {
		return
	}
	emergencyType.ID = id

	updated, err := h.emergencyTypeSvc.UpdateEmergencyType(r.Context(), emergencyType)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "emergency type not found")
			return
		}
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}
	response.JSONResponse(w, http.StatusOK, updated)
}

func decodeEmergencyTypePayload(w http.ResponseWriter, r *http.Request) (*domain.EmergencyType, bool) {
	var emergencyType domain.EmergencyType
	if err := json.NewDecoder(r.Body).Decode(&emergencyType); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid emergency type payload")
		return nil, false
	}
	return &emergencyType, true
}
