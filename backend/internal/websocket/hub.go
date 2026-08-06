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
	Type           EventType   `json:"type"`
	Payload        interface{} `json:"payload"`
	TargetRoles    []string    `json:"-"`
	TargetDeviceID string      `json:"-"`
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
			slog.Info("WebSocket client connected", "remote_addr", client.conn.RemoteAddr().String(), "role", client.Role, "device_id", client.DeviceID)

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
			var deadClients []*Client
			for client := range h.clients {
				// Check role filter if specified
				if len(message.TargetRoles) > 0 {
					matchedRole := false
					for _, r := range message.TargetRoles {
						if client.Role == r {
							matchedRole = true
							break
						}
					}
					// If role didn't match, check if this is the target device ID
					if !matchedRole && (message.TargetDeviceID == "" || client.DeviceID != message.TargetDeviceID) {
						continue
					}
				}

				select {
				case client.send <- bytes:
				default:
					deadClients = append(deadClients, client)
				}
			}
			h.mu.RUnlock()

			if len(deadClients) > 0 {
				h.mu.Lock()
				for _, client := range deadClients {
					if _, ok := h.clients[client]; ok {
						delete(h.clients, client)
						close(client.send)
					}
				}
				h.mu.Unlock()
			}
		}
	}
}

func (h *Hub) Broadcast(msgType EventType, payload interface{}) {
	h.broadcast <- BroadcastMessage{
		Type:    msgType,
		Payload: payload,
	}
}

// BroadcastToAdmins sends messages exclusively to ADMIN, RESPONDER, and DISPATCHER roles
func (h *Hub) BroadcastToAdmins(msgType EventType, payload interface{}) {
	h.broadcast <- BroadcastMessage{
		Type:        msgType,
		Payload:     payload,
		TargetRoles: []string{"ADMIN", "RESPONDER", "DISPATCHER"},
	}
}

// BroadcastToAdminsAndDevice sends messages to responders/admins and optionally to a specific citizen device
func (h *Hub) BroadcastToAdminsAndDevice(msgType EventType, payload interface{}, targetDeviceID string) {
	h.broadcast <- BroadcastMessage{
		Type:           msgType,
		Payload:        payload,
		TargetRoles:    []string{"ADMIN", "RESPONDER", "DISPATCHER"},
		TargetDeviceID: targetDeviceID,
	}
}
