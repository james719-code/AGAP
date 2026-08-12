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

type AlertHandler struct {
	alertSvc service.AlertService
}

func NewAlertHandler(alertSvc service.AlertService) *AlertHandler {
	return &AlertHandler{alertSvc: alertSvc}
}

// List returns active admin alerts for any authenticated user.
// Admin callers may pass ?active_only=false to include archived alerts.
// Supports ?category=WEATHER&severity=CRITICAL&limit=20&offset=0 filtering.
func (h *AlertHandler) List(w http.ResponseWriter, r *http.Request) {
	claims, _ := middleware.GetClaims(r.Context())

	activeOnly := true
	if r.URL.Query().Get("active_only") == "false" && claims != nil && claims.Role == domain.RoleAdmin {
		activeOnly = false
	}

	category := r.URL.Query().Get("category")
	severity := r.URL.Query().Get("severity")
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	offset, _ := strconv.Atoi(r.URL.Query().Get("offset"))

	var userID *int64
	if claims != nil {
		userID = &claims.UserID
	}

	alerts, total, err := h.alertSvc.ListAlerts(r.Context(), userID, activeOnly, category, severity, limit, offset)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"alerts": alerts,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	})
}

// GetByID returns a single admin alert by numeric ID.
func (h *AlertHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid alert ID")
		return
	}

	claims, _ := middleware.GetClaims(r.Context())
	var userID *int64
	if claims != nil {
		userID = &claims.UserID
	}

	alert, err := h.alertSvc.GetAlertByID(r.Context(), id, userID)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "alert not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, alert)
}

// Create creates a new admin alert announcement (Admin only).
func (h *AlertHandler) Create(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "authentication required")
		return
	}

	var alert domain.AdminAlert
	if err := json.NewDecoder(r.Body).Decode(&alert); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid alert payload")
		return
	}
	alert.AuthorID = claims.UserID

	created, err := h.alertSvc.CreateAlert(r.Context(), &alert)
	if err != nil {
		if err == domain.ErrInvalidAlert {
			response.JSONError(w, http.StatusBadRequest, "invalid alert: title, body, category, and severity are required")
			return
		}
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, created)
}

// Update modifies an existing admin alert (Admin only).
func (h *AlertHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid alert ID")
		return
	}

	var alert domain.AdminAlert
	if err := json.NewDecoder(r.Body).Decode(&alert); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid alert payload")
		return
	}
	alert.ID = id

	updated, err := h.alertSvc.UpdateAlert(r.Context(), &alert)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "alert not found")
			return
		}
		if err == domain.ErrInvalidAlert {
			response.JSONError(w, http.StatusBadRequest, "invalid alert: title, body, category, and severity are required")
			return
		}
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, updated)
}

// Delete removes an admin alert (Admin only).
func (h *AlertHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid alert ID")
		return
	}

	if err := h.alertSvc.DeleteAlert(r.Context(), id); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "alert not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// MarkRead marks an alert as read by the authenticated user.
func (h *AlertHandler) MarkRead(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid alert ID")
		return
	}

	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "authentication required")
		return
	}

	if err := h.alertSvc.MarkAlertRead(r.Context(), id, claims.UserID); err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]string{"status": "read"})
}
