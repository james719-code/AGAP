package middleware

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

// IncidentReadAccess keeps citizen incident data private while preserving the
// existing admin/responder API contract. JWTAuth must run before this middleware.
func IncidentReadAccess(telemetrySvc service.TelemetryService) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			claims, ok := GetClaims(r.Context())
			if !ok || claims == nil {
				response.JSONError(w, http.StatusUnauthorized, "unauthorized")
				return
			}
			if claims.Role == domain.RoleAdmin || claims.Role == domain.RoleResponder {
				next.ServeHTTP(w, r)
				return
			}
			if claims.Role != domain.RoleCitizen {
				response.JSONError(w, http.StatusForbidden, "incident access denied")
				return
			}

			incidentID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
			if err != nil || incidentID <= 0 {
				response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
				return
			}
			allowed, err := telemetrySvc.CanCitizenAccessIncident(r.Context(), incidentID, claims.UserID)
			if err != nil {
				response.JSONError(w, http.StatusInternalServerError, "failed to verify incident access")
				return
			}
			if !allowed {
				response.JSONError(w, http.StatusForbidden, "incident access denied")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
