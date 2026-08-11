package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type NotificationRepository interface {
	CreateNotification(ctx context.Context, notif *domain.Notification) (*domain.Notification, error)
	CreateNotificationsForRole(ctx context.Context, role domain.Role, notif *domain.Notification) error
	ListNotificationsByUser(ctx context.Context, userID int64, unreadOnly bool) ([]*domain.Notification, error)
	MarkAsRead(ctx context.Context, notifID int64, userID int64) error
}
