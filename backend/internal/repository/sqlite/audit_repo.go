package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
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

func (r *sqliteAuditRepo) ListAuditLogs(ctx context.Context, filter repository.AuditLogFilter) ([]*domain.AuditLog, error) {
	if filter.Limit <= 0 {
		filter.Limit = 50
	}
	query := `
		SELECT id, actor_id, action, entity_type, entity_id, COALESCE(old_value, ''), COALESCE(new_value, ''), created_at
		FROM audit_logs
	`
	var clauses []string
	var args []interface{}
	if filter.EntityType != "" {
		clauses = append(clauses, "entity_type = ?")
		args = append(args, filter.EntityType)
	}
	if filter.EntityID != 0 {
		clauses = append(clauses, "entity_id = ?")
		args = append(args, filter.EntityID)
	}
	if filter.Action != "" {
		clauses = append(clauses, "action = ?")
		args = append(args, filter.Action)
	}
	if filter.DateFrom != "" {
		clauses = append(clauses, "created_at >= ?")
		args = append(args, filter.DateFrom)
	}
	if filter.DateTo != "" {
		clauses = append(clauses, "created_at <= ?")
		args = append(args, filter.DateTo)
	}
	if len(clauses) > 0 {
		query += " WHERE " + strings.Join(clauses, " AND ")
	}
	query += " ORDER BY created_at DESC LIMIT ?"
	args = append(args, filter.Limit)

	rows, err := r.db.QueryContext(ctx, query, args...)
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
