package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
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
		http.Error(w, `{"error":"invalid responder unit payload"}`, http.StatusBadRequest)
		return
	}

	res, err := h.agencySvc.CreateResponder(r.Context(), &ru)
	if err != nil {
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(res)
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
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(responders)
}

func (h *ResponderHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, `{"error":"invalid responder ID"}`, http.StatusBadRequest)
		return
	}

	responder, err := h.agencySvc.GetResponderByID(r.Context(), id)
	if err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"responder not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(responder)
}

func (h *ResponderHandler) Update(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, `{"error":"invalid responder ID"}`, http.StatusBadRequest)
		return
	}

	var ru domain.ResponderUnit
	if err := json.NewDecoder(r.Body).Decode(&ru); err != nil {
		http.Error(w, `{"error":"invalid payload"}`, http.StatusBadRequest)
		return
	}
	ru.ID = id

	res, err := h.agencySvc.UpdateResponder(r.Context(), &ru)
	if err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"responder not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(res)
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
		http.Error(w, `{"error":"invalid responder ID"}`, http.StatusBadRequest)
		return
	}

	var req UpdateLocationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid payload"}`, http.StatusBadRequest)
		return
	}

	if err := h.agencySvc.UpdateResponderLocation(r.Context(), id, req.Latitude, req.Longitude, req.Status); err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"responder not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":       "success",
		"responder_id": id,
	})
}

func (h *ResponderHandler) Delete(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, `{"error":"invalid responder ID"}`, http.StatusBadRequest)
		return
	}

	if err := h.agencySvc.DeleteResponder(r.Context(), id); err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"responder not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
