package handler

import (
	"net/http"

	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type MaintenanceHandler struct {
	cleanupSvc service.CleanupService
}

func NewMaintenanceHandler(cleanupSvc service.CleanupService) *MaintenanceHandler {
	return &MaintenanceHandler{cleanupSvc: cleanupSvc}
}

func (h *MaintenanceHandler) RunCleanup(w http.ResponseWriter, r *http.Request) {
	result, err := h.cleanupSvc.RunCleanup(r.Context())
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, "failed to run database cleanup: "+err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"status":  "success",
		"message": "System maintenance database purge executed successfully",
		"result":  result,
	})
}
