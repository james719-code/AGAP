package service

import (
	"context"
	"strings"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type MessageService interface {
	ListCatalog(ctx context.Context, includeInactive bool, role string) ([]*domain.PredefinedMessage, error)
	CreateCatalogMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error)
	UpdateCatalogMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error)
	DeleteCatalogMessage(ctx context.Context, id int64) error
	ListIncidentMessages(ctx context.Context, incidentID int64) ([]*domain.IncidentMessage, error)
	CreateIncidentMessage(ctx context.Context, message *domain.IncidentMessage) (*domain.IncidentMessage, error)
}

type messageService struct {
	repo repository.MessageRepository
	hub  *websocket.Hub
}

func NewMessageService(repo repository.MessageRepository, hub *websocket.Hub) MessageService {
	return &messageService{repo: repo, hub: hub}
}

func (s *messageService) ListCatalog(ctx context.Context, includeInactive bool, role string) ([]*domain.PredefinedMessage, error) {
	messages, err := s.repo.ListPredefinedMessages(ctx, includeInactive)
	if err != nil {
		return nil, err
	}
	role = strings.ToUpper(strings.TrimSpace(role))
	if role == "" {
		return messages, nil
	}

	filtered := make([]*domain.PredefinedMessage, 0, len(messages))
	for _, m := range messages {
		if isMessageEligibleForRole(m, role) {
			filtered = append(filtered, m)
		}
	}
	return filtered, nil
}

func isMessageEligibleForRole(m *domain.PredefinedMessage, role string) bool {
	if m.TargetRole != "" && strings.ToUpper(m.TargetRole) != "ALL" {
		return strings.EqualFold(m.TargetRole, role)
	}
	switch role {
	case "CITIZEN":
		return m.MessageType == domain.PredefinedMessageTypeStatus || m.MessageType == domain.PredefinedMessageTypeSOS
	case "RESPONDER":
		return m.MessageType == domain.PredefinedMessageTypeAck || m.MessageType == domain.PredefinedMessageTypeStatus
	default:
		return true
	}
}

func (s *messageService) CreateCatalogMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error) {
	normalizeMessage(message)
	if err := validateMessage(message); err != nil {
		return nil, err
	}
	created, err := s.repo.CreatePredefinedMessage(ctx, message)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "messages", "action": "upsert", "id": created.ID})
	}
	return created, err
}

func (s *messageService) UpdateCatalogMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error) {
	normalizeMessage(message)
	if message.ID <= 0 {
		return nil, domain.ErrNotFound
	}
	if err := validateMessage(message); err != nil {
		return nil, err
	}
	updated, err := s.repo.UpdatePredefinedMessage(ctx, message)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "messages", "action": "upsert", "id": updated.ID})
	}
	return updated, err
}

func (s *messageService) DeleteCatalogMessage(ctx context.Context, id int64) error {
	if err := s.repo.DeletePredefinedMessage(ctx, id); err != nil {
		return err
	}
	if s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "messages", "action": "delete", "id": id})
	}
	return nil
}

func (s *messageService) ListIncidentMessages(ctx context.Context, incidentID int64) ([]*domain.IncidentMessage, error) {
	if incidentID <= 0 {
		return nil, domain.ErrNotFound
	}
	return s.repo.ListIncidentMessages(ctx, incidentID)
}

func (s *messageService) CreateIncidentMessage(ctx context.Context, message *domain.IncidentMessage) (*domain.IncidentMessage, error) {
	message.CustomMessage = strings.TrimSpace(message.CustomMessage)
	if message.Channel == "" {
		message.Channel = domain.IncidentMessageChannelOnline
	}
	if message.IncidentID <= 0 {
		return nil, domain.ErrNotFound
	}
	if !message.Channel.IsValid() {
		return nil, domain.ErrInvalidMessageCatalog
	}
	if message.PredefinedMessageID == nil && message.CustomMessage == "" {
		return nil, domain.ErrInvalidMessageCatalog
	}
	created, err := s.repo.CreateIncidentMessage(ctx, message)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventIncidentMessage, created)
	}
	return created, err
}

func normalizeMessage(message *domain.PredefinedMessage) {
	message.Code = strings.ToUpper(strings.TrimSpace(message.Code))
	message.MessageEN = strings.TrimSpace(message.MessageEN)
	message.MessageFil = strings.TrimSpace(message.MessageFil)
	message.MessageBikol = strings.TrimSpace(message.MessageBikol)
	message.MessageType = domain.PredefinedMessageType(strings.ToUpper(strings.TrimSpace(string(message.MessageType))))
	if message.MessageType == "" {
		message.MessageType = domain.PredefinedMessageTypeAck
	}
}

func validateMessage(message *domain.PredefinedMessage) error {
	if message.Code == "" || message.MessageEN == "" {
		return domain.ErrInvalidMessageCatalog
	}
	if !message.MessageType.IsValid() {
		return domain.ErrInvalidMessageCatalog
	}
	return nil
}
