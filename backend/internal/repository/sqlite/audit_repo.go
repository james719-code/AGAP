package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteAuditRepo struct {
	db *sql.DB
}

func NewAuditRepo(db *sql.DB) repository.AuditRepository {
	return &sqliteAuditRepo{db: db}
}

func (r *sqliteAuditRepo) LogAction(ctx context.Context, log *domain.AuditLog) error {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	query := `
		INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, old_value, new_value, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?)
	`
	_, err := r.db.ExecContext(ctx, query, log.ActorID, log.Action, log.EntityType, log.EntityID, log.OldValue, log.NewValue, nowStr)
	if err != nil {
		return fmt.Errorf("failed to insert audit log: %w", err)
	}
	return nil
}

func (r *sqliteAuditRepo) ListAuditLogs(ctx context.Context, entityType string, entityID int64, limit int) ([]*domain.AuditLog, error) {
	if limit <= 0 {
		limit = 50
	}
	query := `
		SELECT id, actor_id, action, entity_type, entity_id, COALESCE(old_value, ''), COALESCE(new_value, ''), created_at
		FROM audit_logs
		WHERE entity_type = ? AND entity_id = ?
		ORDER BY created_at DESC
		LIMIT ?
	`
	rows, err := r.db.QueryContext(ctx, query, entityType, entityID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var logs []*domain.AuditLog
	for rows.Next() {
		var al domain.AuditLog
		var crTs string
		if err := rows.Scan(&al.ID, &al.ActorID, &al.Action, &al.EntityType, &al.EntityID, &al.OldValue, &al.NewValue, &crTs); err != nil {
			return nil, err
		}
		al.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		logs = append(logs, &al)
	}

	return logs, nil
}
