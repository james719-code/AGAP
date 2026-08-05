package websocket

import (
	"encoding/json"
	"log/slog"
	"sync"
)

type EventType string

const (
	EventNewIncident       EventType = "NEW_INCIDENT"
	EventIncidentDedupped EventType = "INCIDENT_DEDUPLICATED"
	EventIncidentStatus   EventType = "INCIDENT_STATUS_UPDATED"
	EventResponderLocation EventType = "RESPONDER_LOCATION_UPDATED"
)

type BroadcastMessage struct {
	Type    EventType   `json:"type"`
	Payload interface{} `json:"payload"`
}

type Hub struct {
	clients    map[*Client]bool
	broadcast  chan BroadcastMessage
	register   chan *Client
	unregister chan *Client
	mu         sync.RWMutex
}

func NewHub() *Hub {
	return &Hub{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan BroadcastMessage, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
	}
}

func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client] = true
			h.mu.Unlock()
			slog.Info("WebSocket client connected", "remote_addr", client.conn.RemoteAddr().String())

		case client := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				close(client.send)
				slog.Info("WebSocket client disconnected", "remote_addr", client.conn.RemoteAddr().String())
			}
			h.mu.Unlock()

		case message := <-h.broadcast:
			bytes, err := json.Marshal(message)
			if err != nil {
				slog.Error("Failed to marshal WebSocket broadcast message", "error", err)
				continue
			}

			h.mu.RLock()
			for client := range h.clients {
				select {
				case client.send <- bytes:
				default:
					close(client.send)
					delete(h.clients, client)
				}
			}
			h.mu.RUnlock()
		}
	}
}

func (h *Hub) Broadcast(msgType EventType, payload interface{}) {
	h.broadcast <- BroadcastMessage{
		Type:    msgType,
		Payload: payload,
	}
}
