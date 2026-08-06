package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteNotificationRepo struct {
	db *sql.DB
}

func NewNotificationRepo(db *sql.DB) repository.NotificationRepository {
	return &sqliteNotificationRepo{db: db}
}

func (r *sqliteNotificationRepo) CreateNotification(ctx context.Context, notif *domain.Notification) (*domain.Notification, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	query := `
		INSERT INTO notifications (user_id, incident_id, type, payload, is_read, sent_at)
		VALUES (?, ?, ?, ?, 0, ?)
	`
	res, err := r.db.ExecContext(ctx, query, notif.UserID, notif.IncidentID, notif.Type, notif.Payload, nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to create notification: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	notif.ID = id
	notif.IsRead = false
	notif.SentAt, _ = time.Parse(time.RFC3339Nano, nowStr)

	return notif, nil
}

func (r *sqliteNotificationRepo) ListNotificationsByUser(ctx context.Context, userID int64, unreadOnly bool) ([]*domain.Notification, error) {
	var query string
	var args []interface{}

	if unreadOnly {
		query = `SELECT id, user_id, incident_id, type, payload, is_read, sent_at, read_at FROM notifications WHERE user_id = ? AND is_read = 0 ORDER BY sent_at DESC`
		args = []interface{}{userID}
	} else {
		query = `SELECT id, user_id, incident_id, type, payload, is_read, sent_at, read_at FROM notifications WHERE user_id = ? ORDER BY sent_at DESC LIMIT 100`
		args = []interface{}{userID}
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.Notification
	for rows.Next() {
		var n domain.Notification
		var isReadInt int
		var sentTs string
		var readTs sql.NullString

		if err := rows.Scan(&n.ID, &n.UserID, &n.IncidentID, &n.Type, &n.Payload, &isReadInt, &sentTs, &readTs); err != nil {
			return nil, err
		}

		n.IsRead = isReadInt == 1
		n.SentAt, _ = time.Parse(time.RFC3339Nano, sentTs)
		if readTs.Valid && readTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, readTs.String)
			n.ReadAt = &t
		}
		list = append(list, &n)
	}

	return list, nil
}

func (r *sqliteNotificationRepo) MarkAsRead(ctx context.Context, notifID int64, userID int64) error {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	query := `UPDATE notifications SET is_read = 1, read_at = ? WHERE id = ? AND user_id = ?`
	res, err := r.db.ExecContext(ctx, query, nowStr, notifID, userID)
	if err != nil {
		return fmt.Errorf("failed to mark notification as read: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}
