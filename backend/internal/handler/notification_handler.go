package handler

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type NotificationHandler struct {
	notifSvc service.NotificationService
}

func NewNotificationHandler(notifSvc service.NotificationService) *NotificationHandler {
	return &NotificationHandler{notifSvc: notifSvc}
}

func (h *NotificationHandler) List(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	unreadOnly := r.URL.Query().Get("unread") == "true"
	list, err := h.notifSvc.ListNotificationsByUser(r.Context(), claims.UserID, unreadOnly)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, list)
}

func (h *NotificationHandler) MarkRead(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid notification ID")
		return
	}

	if err := h.notifSvc.MarkAsRead(r.Context(), id, claims.UserID); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "notification not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]string{"message": "marked as read"})
}
