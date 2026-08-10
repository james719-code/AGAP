package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"
)

type CleanupRepository interface {
	PurgeOldMeshDeliveryEvents(ctx context.Context, cutoff time.Time) (int64, error)
	PurgeOldMeshPackets(ctx context.Context, cutoff time.Time) (int64, error)
	PurgeExpiredRefreshTokens(ctx context.Context, cutoff time.Time) (int64, error)
	PurgeOldNotifications(ctx context.Context, cutoff time.Time) (int64, error)
	PurgeOldAuditLogs(ctx context.Context, cutoff time.Time) (int64, error)
}

type sqliteCleanupRepo struct {
	db *sql.DB
}

func NewCleanupRepo(db *sql.DB) CleanupRepository {
	return &sqliteCleanupRepo{db: db}
}

func (r *sqliteCleanupRepo) PurgeOldMeshDeliveryEvents(ctx context.Context, cutoff time.Time) (int64, error) {
	cutoffStr := cutoff.UTC().Format(time.RFC3339Nano)
	query := `DELETE FROM mesh_delivery_events WHERE recorded_at < ?`
	res, err := r.db.ExecContext(ctx, query, cutoffStr)
	if err != nil {
		return 0, fmt.Errorf("failed to purge mesh delivery events: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return 0, nil
	}
	return affected, nil
}

func (r *sqliteCleanupRepo) PurgeOldMeshPackets(ctx context.Context, cutoff time.Time) (int64, error) {
	cutoffStr := cutoff.UTC().Format(time.RFC3339Nano)
	// Delete unreferenced mesh packets older than cutoff
	query := `
		DELETE FROM mesh_packets
		WHERE created_at < ?
		  AND incident_id IS NULL
		  AND NOT EXISTS (
			  SELECT 1 FROM mesh_delivery_events mde WHERE mde.packet_id = mesh_packets.id
		  )
	`
	res, err := r.db.ExecContext(ctx, query, cutoffStr)
	if err != nil {
		return 0, fmt.Errorf("failed to purge mesh packets: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return 0, nil
	}
	return affected, nil
}

func (r *sqliteCleanupRepo) PurgeExpiredRefreshTokens(ctx context.Context, cutoff time.Time) (int64, error) {
	cutoffStr := cutoff.UTC().Format(time.RFC3339Nano)
	query := `DELETE FROM refresh_tokens WHERE revoked = 1 OR expires_at < ?`
	res, err := r.db.ExecContext(ctx, query, cutoffStr)
	if err != nil {
		return 0, fmt.Errorf("failed to purge refresh tokens: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return 0, nil
	}
	return affected, nil
}

func (r *sqliteCleanupRepo) PurgeOldNotifications(ctx context.Context, cutoff time.Time) (int64, error) {
	cutoffStr := cutoff.UTC().Format(time.RFC3339Nano)
	query := `DELETE FROM notifications WHERE is_read = 1 AND sent_at < ?`
	res, err := r.db.ExecContext(ctx, query, cutoffStr)
	if err != nil {
		return 0, fmt.Errorf("failed to purge notifications: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return 0, nil
	}
	return affected, nil
}

func (r *sqliteCleanupRepo) PurgeOldAuditLogs(ctx context.Context, cutoff time.Time) (int64, error) {
	cutoffStr := cutoff.UTC().Format(time.RFC3339Nano)
	query := `DELETE FROM audit_logs WHERE created_at < ?`
	res, err := r.db.ExecContext(ctx, query, cutoffStr)
	if err != nil {
		return 0, fmt.Errorf("failed to purge audit logs: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return 0, nil
	}
	return affected, nil
}
