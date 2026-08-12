package websocket

import (
	"encoding/json"
	"log/slog"
	"sync"
)

type EventType string

const (
	EventNewIncident       EventType = "NEW_INCIDENT"
	EventIncidentDedupped  EventType = "INCIDENT_DEDUPLICATED"
	EventIncidentStatus    EventType = "INCIDENT_STATUS_UPDATED"
	EventResponderLocation EventType = "RESPONDER_LOCATION_UPDATED"
	EventIncidentLocation  EventType = "INCIDENT_LOCATION_UPDATED"
	EventCatalogUpdated    EventType = "CATALOG_UPDATED"
	EventIncidentMessage   EventType = "INCIDENT_MESSAGE_CREATED"
	EventIncidentReport    EventType = "INCIDENT_REPORT"
	EventTelemetryUpdated  EventType = "TELEMETRY_UPDATED"
	EventAdminAlert        EventType = "ADMIN_ALERT"
)

type BroadcastMessage struct {
	Type           EventType   `json:"type"`
	Payload        interface{} `json:"payload"`
	TargetRoles    []string    `json:"-"`
	TargetDeviceID string      `json:"-"`
	TargetUserID   int64       `json:"-"`
	TargetUserIDs  []int64     `json:"-"`
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
				// Apply an allow-list whenever any target selector is present. This is
				// important for incident chat: admins receive every operational message,
				// while responders receive only messages for incidents assigned to one of
				// their active rescue units.
				hasTargetFilter := len(message.TargetRoles) > 0 ||
					message.TargetUserID > 0 || len(message.TargetUserIDs) > 0 ||
					message.TargetDeviceID != ""
				if hasTargetFilter {
					matchedRole := false
					for _, role := range message.TargetRoles {
						if client.Role == role {
							matchedRole = true
							break
						}
					}

					matchedUser := message.TargetUserID > 0 && client.UserID == message.TargetUserID
					if !matchedUser && len(message.TargetUserIDs) > 0 {
						for _, userID := range message.TargetUserIDs {
							if userID > 0 && client.UserID == userID {
								matchedUser = true
								break
							}
						}
					}

					matchedDevice := message.TargetDeviceID != "" && client.DeviceID == message.TargetDeviceID
					if !matchedRole && !matchedUser && !matchedDevice {
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

// BroadcastToAdmins sends messages to operational ADMIN and RESPONDER roles.
func (h *Hub) BroadcastToAdmins(msgType EventType, payload interface{}) {
	h.broadcast <- BroadcastMessage{
		Type:        msgType,
		Payload:     payload,
		TargetRoles: []string{"ADMIN", "RESPONDER"},
	}
}

// BroadcastToAdminsAndDevice sends messages to responders/admins and optionally to a specific citizen device
func (h *Hub) BroadcastToAdminsAndDevice(msgType EventType, payload interface{}, targetDeviceID string) {
	h.broadcast <- BroadcastMessage{
		Type:           msgType,
		Payload:        payload,
		TargetRoles:    []string{"ADMIN", "RESPONDER"},
		TargetDeviceID: targetDeviceID,
	}
}

// BroadcastToAdminsAndUser sends an operational event to admins/responders and
// every live WebSocket session belonging to the specified citizen account.
func (h *Hub) BroadcastToAdminsAndUser(msgType EventType, payload interface{}, targetUserID int64) {
	h.broadcast <- BroadcastMessage{
		Type:         msgType,
		Payload:      payload,
		TargetRoles:  []string{"ADMIN", "RESPONDER"},
		TargetUserID: targetUserID,
	}
}

// BroadcastToAdminsAndUsers sends an event to every ADMIN session and to the
// explicitly listed user accounts. It is used for incident-scoped responder
// messaging so unrelated responder devices never receive another unit's chat.
func (h *Hub) BroadcastToAdminsAndUsers(msgType EventType, payload interface{}, userIDs []int64) {
	h.broadcast <- BroadcastMessage{
		Type:          msgType,
		Payload:       payload,
		TargetRoles:   []string{"ADMIN"},
		TargetUserIDs: userIDs,
	}
}
