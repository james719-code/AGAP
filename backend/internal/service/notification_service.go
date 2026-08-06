package service

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type NotificationService interface {
	ListNotificationsByUser(ctx context.Context, userID int64, unreadOnly bool) ([]*domain.Notification, error)
	MarkAsRead(ctx context.Context, notifID int64, userID int64) error
}

type notificationService struct {
	repo repository.NotificationRepository
}

func NewNotificationService(repo repository.NotificationRepository) NotificationService {
	return &notificationService{repo: repo}
}

func (s *notificationService) ListNotificationsByUser(ctx context.Context, userID int64, unreadOnly bool) ([]*domain.Notification, error) {
	return s.repo.ListNotificationsByUser(ctx, userID, unreadOnly)
}

func (s *notificationService) MarkAsRead(ctx context.Context, notifID int64, userID int64) error {
	return s.repo.MarkAsRead(ctx, notifID, userID)
}
