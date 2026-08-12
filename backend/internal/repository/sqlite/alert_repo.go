package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteAlertRepo struct {
	db *sql.DB
}

func NewAlertRepo(db *sql.DB) repository.AlertRepository {
	return &sqliteAlertRepo{db: db}
}

func (r *sqliteAlertRepo) CreateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)

	publishedAt := nowStr
	if !alert.PublishedAt.IsZero() {
		publishedAt = alert.PublishedAt.UTC().Format(time.RFC3339Nano)
	}

	var expiresAt *string
	if alert.ExpiresAt != nil {
		s := alert.ExpiresAt.UTC().Format(time.RFC3339Nano)
		expiresAt = &s
	}

	res, err := r.db.ExecContext(ctx, `
		INSERT INTO admin_alerts (
			author_id, predefined_message_id, title, body, category, severity,
			target_region_code, target_province_code, target_muni_code, target_barangay_code,
			is_pinned, is_active, published_at, expires_at, created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`,
		alert.AuthorID, alert.PredefinedMessageID, alert.Title, alert.Body,
		string(alert.Category), string(alert.Severity),
		nullableString(alert.TargetRegionCode), nullableString(alert.TargetProvinceCode),
		nullableString(alert.TargetMuniCode), nullableString(alert.TargetBarangayCode),
		boolToInt(alert.IsPinned), boolToInt(alert.IsActive),
		publishedAt, expiresAt, nowStr, nowStr,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create admin alert: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	return r.GetAlertByID(ctx, id, nil)
}

func (r *sqliteAlertRepo) GetAlertByID(ctx context.Context, id int64, userID *int64) (*domain.AdminAlert, error) {
	query, args := r.buildSelectOne("a.id = ?", id, userID)
	alert, err := r.scanSingle(ctx, query, args...)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, domain.ErrNotFound
	}
	return alert, err
}

func (r *sqliteAlertRepo) GetAlertByUUID(ctx context.Context, uuid string, userID *int64) (*domain.AdminAlert, error) {
	query, args := r.buildSelectOne("a.uuid = ?", uuid, userID)
	alert, err := r.scanSingle(ctx, query, args...)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, domain.ErrNotFound
	}
	return alert, err
}

func (r *sqliteAlertRepo) ListAlerts(ctx context.Context, userID *int64, activeOnly bool, category string, severity string, limit, offset int) ([]*domain.AdminAlert, int, error) {
	var conditions []string
	var args []interface{}

	if activeOnly {
		conditions = append(conditions, "a.is_active = 1")
		conditions = append(conditions, "(a.expires_at IS NULL OR a.expires_at > strftime('%Y-%m-%dT%H:%M:%fZ','now'))")
	}
	if category != "" {
		conditions = append(conditions, "a.category = ?")
		args = append(args, category)
	}
	if severity != "" {
		conditions = append(conditions, "a.severity = ?")
		args = append(args, severity)
	}

	where := ""
	if len(conditions) > 0 {
		where = "WHERE " + strings.Join(conditions, " AND ")
	}

	// Count total matching results
	countQuery := fmt.Sprintf(`SELECT COUNT(*) FROM admin_alerts a %s`, where)
	var total int
	if err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("failed to count admin alerts: %w", err)
	}

	// Build data query with read-status and author name join
	readSelect := "0 AS is_read"
	readJoin := ""
	if userID != nil {
		readSelect = "COALESCE(ar.id, 0) AS is_read"
		readJoin = fmt.Sprintf("LEFT JOIN admin_alert_reads ar ON ar.alert_id = a.id AND ar.user_id = %d", *userID)
	}

	dataQuery := fmt.Sprintf(`
		SELECT a.id, a.uuid, a.author_id, COALESCE(u.name, u.username, '') AS author_name,
		       a.predefined_message_id, a.title, a.body, a.category, a.severity,
		       COALESCE(a.target_region_code, ''), COALESCE(a.target_province_code, ''),
		       COALESCE(a.target_muni_code, ''), COALESCE(a.target_barangay_code, ''),
		       a.is_pinned, a.is_active, a.published_at, a.expires_at,
		       a.created_at, a.updated_at,
		       (SELECT COUNT(*) FROM admin_alert_reads WHERE alert_id = a.id) AS read_count,
		       %s
		FROM admin_alerts a
		LEFT JOIN users u ON u.id = a.author_id
		%s
		%s
		ORDER BY a.is_pinned DESC, a.published_at DESC
		LIMIT ? OFFSET ?
	`, readSelect, readJoin, where)

	dataArgs := append(args, limit, offset)
	rows, err := r.db.QueryContext(ctx, dataQuery, dataArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list admin alerts: %w", err)
	}
	defer rows.Close()

	alerts := make([]*domain.AdminAlert, 0)
	for rows.Next() {
		alert, err := scanAlert(rows)
		if err != nil {
			return nil, 0, err
		}
		alerts = append(alerts, alert)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	return alerts, total, nil
}

func (r *sqliteAlertRepo) UpdateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)

	var expiresAt *string
	if alert.ExpiresAt != nil {
		s := alert.ExpiresAt.UTC().Format(time.RFC3339Nano)
		expiresAt = &s
	}

	res, err := r.db.ExecContext(ctx, `
		UPDATE admin_alerts
		SET predefined_message_id = ?, title = ?, body = ?, category = ?, severity = ?,
		    target_region_code = ?, target_province_code = ?, target_muni_code = ?, target_barangay_code = ?,
		    is_pinned = ?, is_active = ?, expires_at = ?, updated_at = ?
		WHERE id = ?
	`,
		alert.PredefinedMessageID, alert.Title, alert.Body,
		string(alert.Category), string(alert.Severity),
		nullableString(alert.TargetRegionCode), nullableString(alert.TargetProvinceCode),
		nullableString(alert.TargetMuniCode), nullableString(alert.TargetBarangayCode),
		boolToInt(alert.IsPinned), boolToInt(alert.IsActive),
		expiresAt, nowStr, alert.ID,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to update admin alert: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return nil, domain.ErrNotFound
	}
	return r.GetAlertByID(ctx, alert.ID, nil)
}

func (r *sqliteAlertRepo) DeleteAlert(ctx context.Context, id int64) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM admin_alerts WHERE id = ?`, id)
	if err != nil {
		return fmt.Errorf("failed to delete admin alert: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteAlertRepo) MarkAlertRead(ctx context.Context, alertID int64, userID int64) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT OR IGNORE INTO admin_alert_reads (alert_id, user_id) VALUES (?, ?)
	`, alertID, userID)
	if err != nil {
		return fmt.Errorf("failed to mark alert read: %w", err)
	}
	return nil
}

func (r *sqliteAlertRepo) GetAlertReadCount(ctx context.Context, alertID int64) (int, error) {
	var count int
	err := r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM admin_alert_reads WHERE alert_id = ?`, alertID).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("failed to get alert read count: %w", err)
	}
	return count, nil
}

// ── Helpers ────────────────────────────────────────────────────────────────

func (r *sqliteAlertRepo) buildSelectOne(condition string, condArg interface{}, userID *int64) (string, []interface{}) {
	readSelect := "0 AS is_read"
	readJoin := ""
	args := []interface{}{condArg}

	if userID != nil {
		readSelect = "COALESCE(ar.id, 0) AS is_read"
		readJoin = fmt.Sprintf("LEFT JOIN admin_alert_reads ar ON ar.alert_id = a.id AND ar.user_id = %d", *userID)
	}

	query := fmt.Sprintf(`
		SELECT a.id, a.uuid, a.author_id, COALESCE(u.name, u.username, '') AS author_name,
		       a.predefined_message_id, a.title, a.body, a.category, a.severity,
		       COALESCE(a.target_region_code, ''), COALESCE(a.target_province_code, ''),
		       COALESCE(a.target_muni_code, ''), COALESCE(a.target_barangay_code, ''),
		       a.is_pinned, a.is_active, a.published_at, a.expires_at,
		       a.created_at, a.updated_at,
		       (SELECT COUNT(*) FROM admin_alert_reads WHERE alert_id = a.id) AS read_count,
		       %s
		FROM admin_alerts a
		LEFT JOIN users u ON u.id = a.author_id
		%s
		WHERE %s
	`, readSelect, readJoin, condition)

	return query, args
}

func (r *sqliteAlertRepo) scanSingle(ctx context.Context, query string, args ...interface{}) (*domain.AdminAlert, error) {
	row := r.db.QueryRowContext(ctx, query, args...)
	return scanAlert(row)
}

type alertScanner interface {
	Scan(dest ...interface{}) error
}

func scanAlert(scanner alertScanner) (*domain.AdminAlert, error) {
	var a domain.AdminAlert
	var predefinedMsgID sql.NullInt64
	var isPinned, isActive, readCount, isReadInt int
	var expiresAt sql.NullString
	var publishedAtStr, createdAtStr, updatedAtStr string

	if err := scanner.Scan(
		&a.ID, &a.UUID, &a.AuthorID, &a.AuthorName,
		&predefinedMsgID, &a.Title, &a.Body, &a.Category, &a.Severity,
		&a.TargetRegionCode, &a.TargetProvinceCode,
		&a.TargetMuniCode, &a.TargetBarangayCode,
		&isPinned, &isActive, &publishedAtStr, &expiresAt,
		&createdAtStr, &updatedAtStr,
		&readCount, &isReadInt,
	); err != nil {
		return nil, err
	}

	if predefinedMsgID.Valid {
		a.PredefinedMessageID = &predefinedMsgID.Int64
	}
	a.IsPinned = isPinned == 1
	a.IsActive = isActive == 1
	a.ReadCount = readCount
	a.IsRead = isReadInt > 0
	a.PublishedAt, _ = time.Parse(time.RFC3339Nano, publishedAtStr)
	a.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdAtStr)
	a.UpdatedAt, _ = time.Parse(time.RFC3339Nano, updatedAtStr)
	if expiresAt.Valid {
		t, _ := time.Parse(time.RFC3339Nano, expiresAt.String)
		a.ExpiresAt = &t
	}

	return &a, nil
}

