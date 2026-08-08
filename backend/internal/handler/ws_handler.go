package handler

import (
	"net/http"
	"strings"

	"github.com/travelingsalesman/agap/backend/internal/service"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type WSHandler struct {
	hub            *websocket.Hub
	authSvc        service.AuthService
	allowedOrigins []string
}

func NewWSHandler(hub *websocket.Hub, authSvc service.AuthService, allowedOrigins ...[]string) *WSHandler {
	var origins []string
	if len(allowedOrigins) > 0 {
		origins = allowedOrigins[0]
	}
	return &WSHandler{
		hub:            hub,
		authSvc:        authSvc,
		allowedOrigins: origins,
	}
}

func (h *WSHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if h.authSvc == nil {
		http.Error(w, "websocket authentication unavailable", http.StatusServiceUnavailable)
		return
	}

	tokenStr := r.URL.Query().Get("token")
	if tokenStr == "" {
		authHeader := r.Header.Get("Authorization")
		if strings.HasPrefix(authHeader, "Bearer ") {
			tokenStr = strings.TrimPrefix(authHeader, "Bearer ")
		}
	}
	if tokenStr == "" {
		http.Error(w, "missing websocket token", http.StatusUnauthorized)
		return
	}

	claims, err := h.authSvc.ValidateToken(tokenStr)
	if err != nil || claims == nil {
		http.Error(w, "invalid websocket token", http.StatusUnauthorized)
		return
	}

	websocket.ServeWS(h.hub, w, r, string(claims.Role), claims.DeviceID, claims.UserID, h.allowedOrigins)
}
