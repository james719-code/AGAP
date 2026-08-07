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

type sqliteDispatchRepo struct {
	db *sql.DB
}

func NewDispatchRepo(db *sql.DB) repository.DispatchRepository {
	return &sqliteDispatchRepo{db: db}
}

func (r *sqliteDispatchRepo) CreateDispatch(ctx context.Context, dispatch *domain.IncidentDispatch) (*domain.IncidentDispatch, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	query := `
		INSERT INTO incident_dispatches (incident_id, unit_id, dispatched_by, status, dispatched_at)
		VALUES (?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query, dispatch.IncidentID, dispatch.UnitID, dispatch.DispatchedBy, string(dispatch.Status), nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to create dispatch: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	return r.GetDispatchByID(ctx, id)
}

func (r *sqliteDispatchRepo) GetDispatchByID(ctx context.Context, id int64) (*domain.IncidentDispatch, error) {
	query := `
		SELECT d.id, d.incident_id, d.unit_id, d.dispatched_by, d.status, d.dispatched_at, d.accepted_at, d.departed_at, d.arrived_at, d.completed_at,
		       ru.unit_code, a.name, COALESCE(ru.responder_name, '')
		FROM incident_dispatches d
		JOIN rescue_units ru ON d.unit_id = ru.id
		JOIN agencies a ON ru.agency_id = a.id
		WHERE d.id = ?
	`
	var d domain.IncidentDispatch
	var statusStr, dispTs string
	var accTs, depTs, arrTs, compTs sql.NullString

	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&d.ID, &d.IncidentID, &d.UnitID, &d.DispatchedBy, &statusStr, &dispTs, &accTs, &depTs, &arrTs, &compTs,
		&d.UnitCallsign, &d.AgencyName, &d.ResponderName,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	d.Status = domain.DispatchStatus(statusStr)
	d.DispatchedAt, _ = time.Parse(time.RFC3339Nano, dispTs)
	if accTs.Valid && accTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, accTs.String)
		d.AcceptedAt = &t
	}
	if depTs.Valid && depTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, depTs.String)
		d.DepartedAt = &t
	}
	if arrTs.Valid && arrTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, arrTs.String)
		d.ArrivedAt = &t
	}
	if compTs.Valid && compTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, compTs.String)
		d.CompletedAt = &t
	}

	return &d, nil
}

func (r *sqliteDispatchRepo) ListDispatchesByIncident(ctx context.Context, incidentID int64) ([]*domain.IncidentDispatch, error) {
	query := `
		SELECT d.id, d.incident_id, d.unit_id, d.dispatched_by, d.status, d.dispatched_at, d.accepted_at, d.departed_at, d.arrived_at, d.completed_at,
		       ru.unit_code, a.name, COALESCE(ru.responder_name, '')
		FROM incident_dispatches d
		JOIN rescue_units ru ON d.unit_id = ru.id
		JOIN agencies a ON ru.agency_id = a.id
		WHERE d.incident_id = ?
		ORDER BY d.dispatched_at DESC
	`
	rows, err := r.db.QueryContext(ctx, query, incidentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.IncidentDispatch
	for rows.Next() {
		var d domain.IncidentDispatch
		var statusStr, dispTs string
		var accTs, depTs, arrTs, compTs sql.NullString

		if err := rows.Scan(
			&d.ID, &d.IncidentID, &d.UnitID, &d.DispatchedBy, &statusStr, &dispTs, &accTs, &depTs, &arrTs, &compTs,
			&d.UnitCallsign, &d.AgencyName, &d.ResponderName,
		); err != nil {
			return nil, err
		}

		d.Status = domain.DispatchStatus(statusStr)
		d.DispatchedAt, _ = time.Parse(time.RFC3339Nano, dispTs)
		if accTs.Valid && accTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, accTs.String)
			d.AcceptedAt = &t
		}
		if depTs.Valid && depTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, depTs.String)
			d.DepartedAt = &t
		}
		if arrTs.Valid && arrTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, arrTs.String)
			d.ArrivedAt = &t
		}
		if compTs.Valid && compTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, compTs.String)
			d.CompletedAt = &t
		}
		list = append(list, &d)
	}

	return list, nil
}

func (r *sqliteDispatchRepo) ListDispatchesByResponderUnit(ctx context.Context, unitID int64) ([]*domain.IncidentDispatch, error) {
	query := `
		SELECT d.id, d.incident_id, d.unit_id, d.dispatched_by, d.status, d.dispatched_at, d.accepted_at, d.departed_at, d.arrived_at, d.completed_at,
		       ru.unit_code, a.name, COALESCE(ru.responder_name, '')
		FROM incident_dispatches d
		JOIN rescue_units ru ON d.unit_id = ru.id
		JOIN agencies a ON ru.agency_id = a.id
		WHERE d.unit_id = ?
		ORDER BY d.dispatched_at DESC
	`
	rows, err := r.db.QueryContext(ctx, query, unitID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.IncidentDispatch
	for rows.Next() {
		var d domain.IncidentDispatch
		var statusStr, dispTs string
		var accTs, depTs, arrTs, compTs sql.NullString

		if err := rows.Scan(
			&d.ID, &d.IncidentID, &d.UnitID, &d.DispatchedBy, &statusStr, &dispTs, &accTs, &depTs, &arrTs, &compTs,
			&d.UnitCallsign, &d.AgencyName, &d.ResponderName,
		); err != nil {
			return nil, err
		}

		d.Status = domain.DispatchStatus(statusStr)
		d.DispatchedAt, _ = time.Parse(time.RFC3339Nano, dispTs)
		if accTs.Valid && accTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, accTs.String)
			d.AcceptedAt = &t
		}
		if depTs.Valid && depTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, depTs.String)
			d.DepartedAt = &t
		}
		if arrTs.Valid && arrTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, arrTs.String)
			d.ArrivedAt = &t
		}
		if compTs.Valid && compTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, compTs.String)
			d.CompletedAt = &t
		}
		list = append(list, &d)
	}

	return list, nil
}

func (r *sqliteDispatchRepo) UpdateDispatchStatus(ctx context.Context, id int64, status domain.DispatchStatus) error {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	var query string
	var args []interface{}

	switch status {
	case domain.DispatchStatusAccepted:
		query = `UPDATE incident_dispatches SET status = ?, accepted_at = COALESCE(accepted_at, ?) WHERE id = ?`
		args = []interface{}{string(status), nowStr, id}
	case domain.DispatchStatusEnRoute:
		query = `UPDATE incident_dispatches SET status = ?, departed_at = COALESCE(departed_at, ?) WHERE id = ?`
		args = []interface{}{string(status), nowStr, id}
	case domain.DispatchStatusOnScene:
		query = `UPDATE incident_dispatches SET status = ?, arrived_at = COALESCE(arrived_at, ?) WHERE id = ?`
		args = []interface{}{string(status), nowStr, id}
	case domain.DispatchStatusCompleted:
		query = `UPDATE incident_dispatches SET status = ?, completed_at = COALESCE(completed_at, ?) WHERE id = ?`
		args = []interface{}{string(status), nowStr, id}
	default:
		query = `UPDATE incident_dispatches SET status = ? WHERE id = ?`
		args = []interface{}{string(status), id}
	}

	res, err := r.db.ExecContext(ctx, query, args...)
	if err != nil {
		return fmt.Errorf("failed to update dispatch status: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}
