package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type MessageHandler struct {
	messageSvc service.MessageService
}

func NewMessageHandler(messageSvc service.MessageService) *MessageHandler {
	return &MessageHandler{messageSvc: messageSvc}
}

func (h *MessageHandler) ListCatalog(w http.ResponseWriter, r *http.Request) {
	includeInactive := r.URL.Query().Get("include_inactive") == "true"
	if includeInactive {
		claims, ok := middleware.GetClaims(r.Context())
		if !ok || claims == nil || claims.Role != domain.RoleAdmin {
			response.JSONError(w, http.StatusForbidden, "inactive catalog entries require admin access")
			return
		}
	}

	messages, err := h.messageSvc.ListCatalog(r.Context(), includeInactive)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, messages)
}

func (h *MessageHandler) CreateCatalog(w http.ResponseWriter, r *http.Request) {
	message, ok := decodePredefinedMessagePayload(w, r)
	if !ok {
		return
	}
	if !message.IsActive {
		message.IsActive = true
	}

	created, err := h.messageSvc.CreateCatalogMessage(r.Context(), message)
	if err != nil {
		status := http.StatusBadRequest
		if err == domain.ErrAlreadyExists {
			status = http.StatusConflict
		}
		response.JSONError(w, status, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, created)
}

func (h *MessageHandler) UpdateCatalog(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid predefined message ID")
		return
	}

	message, ok := decodePredefinedMessagePayload(w, r)
	if !ok {
		return
	}
	message.ID = id

	updated, err := h.messageSvc.UpdateCatalogMessage(r.Context(), message)
	if err != nil {
		switch err {
		case domain.ErrNotFound:
			response.JSONError(w, http.StatusNotFound, "predefined message not found")
		default:
			response.JSONError(w, http.StatusBadRequest, err.Error())
		}
		return
	}

	response.JSONResponse(w, http.StatusOK, updated)
}

func (h *MessageHandler) DeleteCatalog(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid predefined message ID")
		return
	}

	if err := h.messageSvc.DeleteCatalogMessage(r.Context(), id); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "predefined message not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

type createIncidentMessageRequest struct {
	ReceiverUserID      *int64                        `json:"receiver_user_id,omitempty"`
	PredefinedMessageID *int64                        `json:"predefined_message_id,omitempty"`
	CustomMessage       string                        `json:"custom_message,omitempty"`
	Channel             domain.IncidentMessageChannel `json:"channel,omitempty"`
	// Legacy Android alias for custom_message.
	MessageContent string `json:"message_content,omitempty"`
}

func (h *MessageHandler) ListIncidentMessages(w http.ResponseWriter, r *http.Request) {
	incidentID, ok := parseIncidentMessageIncidentID(w, r)
	if !ok {
		return
	}

	messages, err := h.messageSvc.ListIncidentMessages(r.Context(), incidentID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, messages)
}

func (h *MessageHandler) CreateIncidentMessage(w http.ResponseWriter, r *http.Request) {
	incidentID, ok := parseIncidentMessageIncidentID(w, r)
	if !ok {
		return
	}

	var req createIncidentMessageRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid incident message payload")
		return
	}

	var senderUserID *int64
	if claims, ok := middleware.GetClaims(r.Context()); ok && claims != nil {
		senderUserID = &claims.UserID
	}
	customMessage := req.CustomMessage
	if customMessage == "" {
		customMessage = req.MessageContent
	}

	created, err := h.messageSvc.CreateIncidentMessage(r.Context(), &domain.IncidentMessage{
		IncidentID:          incidentID,
		SenderUserID:        senderUserID,
		ReceiverUserID:      req.ReceiverUserID,
		PredefinedMessageID: req.PredefinedMessageID,
		CustomMessage:       customMessage,
		Channel:             req.Channel,
	})
	if err != nil {
		switch err {
		case domain.ErrNotFound:
			response.JSONError(w, http.StatusNotFound, "incident not found")
		default:
			response.JSONError(w, http.StatusBadRequest, err.Error())
		}
		return
	}

	response.JSONResponse(w, http.StatusCreated, created)
}

func decodePredefinedMessagePayload(w http.ResponseWriter, r *http.Request) (*domain.PredefinedMessage, bool) {
	var message domain.PredefinedMessage
	if err := json.NewDecoder(r.Body).Decode(&message); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid predefined message payload")
		return nil, false
	}
	return &message, true
}

func parseIncidentMessageIncidentID(w http.ResponseWriter, r *http.Request) (int64, bool) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid incident ID")
		return 0, false
	}
	return id, true
}
