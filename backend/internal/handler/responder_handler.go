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

type ResponderHandler struct {
	agencySvc service.AgencyService
}

func NewResponderHandler(agencySvc service.AgencyService) *ResponderHandler {
	return &ResponderHandler{agencySvc: agencySvc}
}

func (h *ResponderHandler) Create(w http.ResponseWriter, r *http.Request) {
	var ru domain.ResponderUnit
	if err := json.NewDecoder(r.Body).Decode(&ru); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder unit payload")
		return
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

func (h *ResponderHandler) Update(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid responder ID")
		return
	}

	var ru domain.ResponderUnit
	if err := json.NewDecoder(r.Body).Decode(&ru); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}
	ru.ID = id

	res, err := h.agencySvc.UpdateResponder(r.Context(), &ru)
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

type UpdateLocationRequest struct {
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

	var req UpdateLocationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
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

