package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type AgencyHandler struct {
	agencySvc service.AgencyService
}

func NewAgencyHandler(agencySvc service.AgencyService) *AgencyHandler {
	return &AgencyHandler{agencySvc: agencySvc}
}

func (h *AgencyHandler) Create(w http.ResponseWriter, r *http.Request) {
	var a domain.Agency
	if err := json.NewDecoder(r.Body).Decode(&a); err != nil {
		http.Error(w, `{"error":"invalid agency payload"}`, http.StatusBadRequest)
		return
	}

	res, err := h.agencySvc.CreateAgency(r.Context(), &a)
	if err != nil {
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(res)
}

func (h *AgencyHandler) List(w http.ResponseWriter, r *http.Request) {
	agencies, err := h.agencySvc.ListAgencies(r.Context())
	if err != nil {
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(agencies)
}

func (h *AgencyHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, `{"error":"invalid agency ID"}`, http.StatusBadRequest)
		return
	}

	agency, err := h.agencySvc.GetAgencyByID(r.Context(), id)
	if err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"agency not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(agency)
}

func (h *AgencyHandler) Update(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, `{"error":"invalid agency ID"}`, http.StatusBadRequest)
		return
	}

	var a domain.Agency
	if err := json.NewDecoder(r.Body).Decode(&a); err != nil {
		http.Error(w, `{"error":"invalid agency payload"}`, http.StatusBadRequest)
		return
	}
	a.ID = id

	res, err := h.agencySvc.UpdateAgency(r.Context(), &a)
	if err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"agency not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(res)
}

func (h *AgencyHandler) Delete(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, `{"error":"invalid agency ID"}`, http.StatusBadRequest)
		return
	}

	if err := h.agencySvc.DeleteAgency(r.Context(), id); err != nil {
		if err == domain.ErrNotFound {
			http.Error(w, `{"error":"agency not found"}`, http.StatusNotFound)
			return
		}
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
