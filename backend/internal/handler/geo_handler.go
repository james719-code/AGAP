package handler

import (
	"net/http"
	"strconv"

	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type GeoHandler struct {
	geoSvc service.GeoService
}

func NewGeoHandler(geoSvc service.GeoService) *GeoHandler {
	return &GeoHandler{geoSvc: geoSvc}
}

func (h *GeoHandler) ListProvinces(w http.ResponseWriter, r *http.Request) {
	list, err := h.geoSvc.ListProvinces(r.Context())
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	response.JSONResponse(w, http.StatusOK, list)
}

func (h *GeoHandler) ListMunicipalities(w http.ResponseWriter, r *http.Request) {
	provinceID, _ := strconv.ParseInt(r.URL.Query().Get("province_id"), 10, 64)
	list, err := h.geoSvc.ListMunicipalities(r.Context(), provinceID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	response.JSONResponse(w, http.StatusOK, list)
}

func (h *GeoHandler) ListBarangays(w http.ResponseWriter, r *http.Request) {
	municipalityID, _ := strconv.ParseInt(r.URL.Query().Get("municipality_id"), 10, 64)
	list, err := h.geoSvc.ListBarangays(r.Context(), municipalityID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	response.JSONResponse(w, http.StatusOK, list)
}

func (h *GeoHandler) ListAddresses(w http.ResponseWriter, r *http.Request) {
	barangayID, _ := strconv.ParseInt(r.URL.Query().Get("barangay_id"), 10, 64)
	list, err := h.geoSvc.ListAddresses(r.Context(), barangayID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	response.JSONResponse(w, http.StatusOK, list)
}
