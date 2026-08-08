package handler

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type ReportHandler struct {
	reportSvc service.ReportService
}

func NewReportHandler(reportSvc service.ReportService) *ReportHandler {
	return &ReportHandler{reportSvc: reportSvc}
}

func (h *ReportHandler) CreateReport(w http.ResponseWriter, r *http.Request) {
	var req domain.CreateReportRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "Invalid JSON payload")
		return
	}

	claims, ok := middleware.GetClaims(r.Context())
	var userID *int64
	if ok && claims != nil && claims.UserID > 0 {
		uid := claims.UserID
		userID = &uid
	}

	report, err := h.reportSvc.CreateOrEnrichReport(r.Context(), &req, userID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, map[string]any{
		"success": true,
		"message": "Report received successfully",
		"report":  report,
	})
}

func (h *ReportHandler) GetMyReports(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil || claims.UserID == 0 {
		response.JSONError(w, http.StatusUnauthorized, "User identity required")
		return
	}

	reports, err := h.reportSvc.GetUserReports(r.Context(), claims.UserID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]any{
		"success": true,
		"reports": reports,
	})
}

func (h *ReportHandler) GetMyReportByUUID(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil || claims.UserID == 0 {
		response.JSONError(w, http.StatusUnauthorized, "User identity required")
		return
	}

	uuidStr := chi.URLParam(r, "uuid")
	report, err := h.reportSvc.GetReportByUUID(r.Context(), uuidStr)
	if err != nil {
		if err == service.ErrReportNotFound {
			response.JSONError(w, http.StatusNotFound, "Report not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	if report.ReporterUserID == nil || *report.ReporterUserID != claims.UserID {
		response.JSONError(w, http.StatusForbidden, "Access denied to other user's report")
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]any{
		"success": true,
		"report":  report,
	})
}
