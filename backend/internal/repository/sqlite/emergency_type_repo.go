package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteEmergencyTypeRepo struct {
	db *sql.DB
}

func NewEmergencyTypeRepo(db *sql.DB) repository.EmergencyTypeRepository {
	return &sqliteEmergencyTypeRepo{db: db}
}

func (r *sqliteEmergencyTypeRepo) ListEmergencyTypes(ctx context.Context, includeInactive bool) ([]*domain.EmergencyType, error) {
	query := `SELECT id, code, name, COALESCE(description, ''), default_priority, COALESCE(protocol_code, 0), COALESCE(target_agency_type, ''), is_active, created_at, updated_at FROM emergency_types`
	if !includeInactive {
		query += ` WHERE is_active = 1`
	}
	query += ` ORDER BY default_priority ASC, name ASC`

	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to list emergency types: %w", err)
	}
	defer rows.Close()

	var emergencyTypes []*domain.EmergencyType
	for rows.Next() {
		emergencyType, err := scanEmergencyType(rows)
		if err != nil {
			return nil, err
		}
		emergencyTypes = append(emergencyTypes, emergencyType)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return emergencyTypes, nil
}

func (r *sqliteEmergencyTypeRepo) CreateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	res, err := r.db.ExecContext(ctx, `
		INSERT INTO emergency_types
			(code, name, description, default_priority, protocol_code, target_agency_type, is_active, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, emergencyType.Code, emergencyType.Name, emergencyType.Description, emergencyType.DefaultPriority, emergencyType.ProtocolCode, emergencyType.TargetAgencyType, boolToInt(emergencyType.IsActive), nowStr, nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to create emergency type: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}
	return r.getEmergencyTypeByID(ctx, id)
}

func (r *sqliteEmergencyTypeRepo) UpdateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	res, err := r.db.ExecContext(ctx, `
		UPDATE emergency_types
		SET code = ?, name = ?, description = ?, default_priority = ?, protocol_code = ?, target_agency_type = ?, is_active = ?, updated_at = ?
		WHERE id = ?
	`, emergencyType.Code, emergencyType.Name, emergencyType.Description, emergencyType.DefaultPriority, emergencyType.ProtocolCode, emergencyType.TargetAgencyType, boolToInt(emergencyType.IsActive), nowStr, emergencyType.ID)
	if err != nil {
		return nil, fmt.Errorf("failed to update emergency type: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return nil, domain.ErrNotFound
	}
	return r.getEmergencyTypeByID(ctx, emergencyType.ID)
}

func (r *sqliteEmergencyTypeRepo) DeleteEmergencyType(ctx context.Context, id int64) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM emergency_types WHERE id = ?`, id)
	if err != nil {
		return fmt.Errorf("failed to delete emergency type: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteEmergencyTypeRepo) getEmergencyTypeByID(ctx context.Context, id int64) (*domain.EmergencyType, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id, code, name, COALESCE(description, ''), default_priority, COALESCE(protocol_code, 0), COALESCE(target_agency_type, ''), is_active, created_at, updated_at
		FROM emergency_types
		WHERE id = ?
	`, id)
	emergencyType, err := scanEmergencyType(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, domain.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return emergencyType, nil
}

type emergencyTypeScanner interface {
	Scan(dest ...interface{}) error
}

func scanEmergencyType(scanner emergencyTypeScanner) (*domain.EmergencyType, error) {
	var emergencyType domain.EmergencyType
	var isActive int
	var createdAt string
	var updatedAt string

	if err := scanner.Scan(
		&emergencyType.ID,
		&emergencyType.Code,
		&emergencyType.Name,
		&emergencyType.Description,
		&emergencyType.DefaultPriority,
		&emergencyType.ProtocolCode,
		&emergencyType.TargetAgencyType,
		&isActive,
		&createdAt,
		&updatedAt,
	); err != nil {
		return nil, err
	}

	emergencyType.IsActive = isActive == 1
	emergencyType.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdAt)
	emergencyType.UpdatedAt, _ = time.Parse(time.RFC3339Nano, updatedAt)

	return &emergencyType, nil
}
