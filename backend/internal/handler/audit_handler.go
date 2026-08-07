package handler

import (
	"net/http"
	"strconv"

	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type AuditHandler struct {
	auditRepo repository.AuditRepository
}

func NewAuditHandler(auditRepo repository.AuditRepository) *AuditHandler {
	return &AuditHandler{auditRepo: auditRepo}
}

func (h *AuditHandler) List(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	filter := repository.AuditLogFilter{
		EntityType: q.Get("entity_type"),
		Action:     q.Get("action"),
		DateFrom:   q.Get("date_from"),
		DateTo:     q.Get("date_to"),
	}
	entityID, _ := strconv.ParseInt(q.Get("entity_id"), 10, 64)
	filter.EntityID = entityID
	limit, _ := strconv.Atoi(q.Get("limit"))
	filter.Limit = limit

	if entityID != 0 && filter.EntityType == "" {
		response.JSONError(w, http.StatusBadRequest, "entity_type is required when entity_id is provided")
		return
	}

	logs, err := h.auditRepo.ListAuditLogs(r.Context(), filter)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, logs)
}
