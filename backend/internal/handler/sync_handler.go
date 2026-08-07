package handler

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type SyncHandler struct {
	syncSvc service.SyncService
}

func NewSyncHandler(syncSvc service.SyncService) *SyncHandler {
	return &SyncHandler{syncSvc: syncSvc}
}

func (h *SyncHandler) Bootstrap(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	payload, err := h.syncSvc.Bootstrap(r.Context(), claims.UserID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, payload)
}

func (h *SyncHandler) Push(w http.ResponseWriter, r *http.Request) {
	var req service.SyncPushRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid sync push payload")
		return
	}

	result, err := h.syncSvc.Push(r.Context(), req)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusAccepted, result)
}

func (h *SyncHandler) Changes(w http.ResponseWriter, r *http.Request) {
	sinceParam := r.URL.Query().Get("since")
	if sinceParam == "" {
		response.JSONError(w, http.StatusBadRequest, "since query parameter is required")
		return
	}
	since, err := time.Parse(time.RFC3339Nano, sinceParam)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "since must be an RFC3339 timestamp")
		return
	}

	payload, err := h.syncSvc.Changes(r.Context(), since)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, payload)
}
