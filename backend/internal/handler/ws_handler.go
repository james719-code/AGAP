package handler

import (
	"net/http"
	"strings"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/service"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type WSHandler struct {
	hub     *websocket.Hub
	authSvc service.AuthService
}

func NewWSHandler(hub *websocket.Hub, authSvc service.AuthService) *WSHandler {
	return &WSHandler{
		hub:     hub,
		authSvc: authSvc,
	}
}

func (h *WSHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	tokenStr := r.URL.Query().Get("token")
	if tokenStr == "" {
		authHeader := r.Header.Get("Authorization")
		if strings.HasPrefix(authHeader, "Bearer ") {
			tokenStr = strings.TrimPrefix(authHeader, "Bearer ")
		}
	}

	var role domain.Role = domain.RoleCitizen
	var deviceID string
	var userID int64

	if tokenStr != "" && h.authSvc != nil {
		claims, err := h.authSvc.ValidateToken(tokenStr)
		if err == nil && claims != nil {
			role = claims.Role
			deviceID = claims.DeviceID
			userID = claims.UserID
		}
	}

	websocket.ServeWS(h.hub, w, r, string(role), deviceID, userID)
}
