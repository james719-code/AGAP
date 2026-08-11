package websocket

import (
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 512
)

func newUpgrader(allowedOrigins []string) websocket.Upgrader {
	return websocket.Upgrader{
		ReadBufferSize:  1024,
		WriteBufferSize: 1024,
		CheckOrigin: func(r *http.Request) bool {
			origin := strings.TrimSpace(r.Header.Get("Origin"))

			// Native Android/WebSocket clients normally omit Origin.
			if origin == "" {
				return true
			}

			for _, allowed := range allowedOrigins {
				allowed = strings.TrimSpace(allowed)

				if allowed == "*" || strings.EqualFold(allowed, origin) {
					return true
				}
			}

			// Allow same-origin browser WebSocket connections.
			parsed, err := url.Parse(origin)
			if err != nil {
				return false
			}

			return strings.EqualFold(parsed.Host, r.Host)
		},
	}
}

type Client struct {
	hub      *Hub
	conn     *websocket.Conn
	send     chan []byte
	Role     string
	DeviceID string
	UserID   int64
}

func ServeWS(
	hub *Hub,
	w http.ResponseWriter,
	r *http.Request,
	role string,
	deviceID string,
	userID int64,
	allowedOrigins ...[]string,
) {
	var origins []string

	if len(allowedOrigins) > 0 {
		origins = allowedOrigins[0]
	}

	upgrader := newUpgrader(origins)

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		slog.Error(
			"Failed to upgrade HTTP to WebSocket",
			"error", err,
		)
		return
	}

	client := &Client{
		hub:      hub,
		conn:     conn,
		send:     make(chan []byte, 256),
		Role:     role,
		DeviceID: deviceID,
		UserID:   userID,
	}

	client.hub.register <- client

	go client.writePump()
	go client.readPump()
}

func (c *Client) readPump() {
	defer func() {
		c.hub.unregister <- c

		if err := c.conn.Close(); err != nil {
			slog.Debug(
				"WebSocket connection close error",
				"error", err,
			)
		}
	}()

	c.conn.SetReadLimit(maxMessageSize)

	if err := c.conn.SetReadDeadline(
		time.Now().Add(pongWait),
	); err != nil {
		slog.Debug(
			"Failed to set WebSocket read deadline",
			"error", err,
		)
	}

	c.conn.SetPongHandler(func(string) error {
		return c.conn.SetReadDeadline(
			time.Now().Add(pongWait),
		)
	})

	for {
		_, _, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(
				err,
				websocket.CloseGoingAway,
				websocket.CloseAbnormalClosure,
			) {
				slog.Warn(
					"WebSocket unexpected close error",
					"error", err,
					"user_id", c.UserID,
					"device_id", c.DeviceID,
					"role", c.Role,
				)
			}

			break
		}
	}
}

func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)

	defer func() {
		ticker.Stop()

		if err := c.conn.Close(); err != nil {
			slog.Debug(
				"WebSocket connection close error",
				"error", err,
			)
		}
	}()

	for {
		select {
		case message, ok := <-c.send:
			if err := c.conn.SetWriteDeadline(
				time.Now().Add(writeWait),
			); err != nil {
				slog.Debug(
					"Failed to set WebSocket write deadline",
					"error", err,
				)
				return
			}

			if !ok {
				if err := c.conn.WriteMessage(
					websocket.CloseMessage,
					[]byte{},
				); err != nil {
					slog.Debug(
						"Failed to send WebSocket close message",
						"error", err,
					)
				}

				return
			}

			writer, err := c.conn.NextWriter(
				websocket.TextMessage,
			)
			if err != nil {
				slog.Debug(
					"Failed to create WebSocket writer",
					"error", err,
				)
				return
			}

			if _, err := writer.Write(message); err != nil {
				_ = writer.Close()

				slog.Debug(
					"Failed to write WebSocket message",
					"error", err,
				)

				return
			}

			if err := writer.Close(); err != nil {
				slog.Debug(
					"Failed to close WebSocket writer",
					"error", err,
				)
				return
			}

		case <-ticker.C:
			if err := c.conn.SetWriteDeadline(
				time.Now().Add(writeWait),
			); err != nil {
				slog.Debug(
					"Failed to set WebSocket ping deadline",
					"error", err,
				)
				return
			}

			if err := c.conn.WriteMessage(
				websocket.PingMessage,
				nil,
			); err != nil {
				slog.Debug(
					"Failed to send WebSocket ping",
					"error", err,
				)
				return
			}
		}
	}
}
