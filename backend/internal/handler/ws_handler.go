package handler

import (
	"net/http"

	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type WSHandler struct {
	hub *websocket.Hub
}

func NewWSHandler(hub *websocket.Hub) *WSHandler {
	return &WSHandler{hub: hub}
}

func (h *WSHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	websocket.ServeWS(h.hub, w, r)
}
