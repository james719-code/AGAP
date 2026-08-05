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

type sqliteAgencyRepo struct {
	db *sql.DB
}

func NewAgencyRepo(db *sql.DB) repository.AgencyRepository {
	return &sqliteAgencyRepo{db: db}
}

// Agency CRUD
func (r *sqliteAgencyRepo) CreateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	query := `
		INSERT INTO agencies (code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query,
		agency.Code, agency.Name, string(agency.Type), agency.RegionCode, agency.ProvinceCode,
		agency.MuniCode, agency.BarangayCode, agency.ContactNumber, agency.Latitude, agency.Longitude,
		nowStr, nowStr,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create agency: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	agency.ID = id
	agency.CreatedAt = now
	agency.UpdatedAt = now
	return agency, nil
}

func (r *sqliteAgencyRepo) GetAgencyByID(ctx context.Context, id int64) (*domain.Agency, error) {
	query := `SELECT id, code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, created_at, updated_at FROM agencies WHERE id = ?`
	row := r.db.QueryRowContext(ctx, query, id)

	var a domain.Agency
	var agencyType, crTs, upTs string
	err := row.Scan(&a.ID, &a.Code, &a.Name, &agencyType, &a.RegionCode, &a.ProvinceCode, &a.MuniCode, &a.BarangayCode, &a.ContactNumber, &a.Latitude, &a.Longitude, &crTs, &upTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}
	a.Type = domain.AgencyType(agencyType)
	a.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	a.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &a, nil
}

func (r *sqliteAgencyRepo) GetAgencyByCode(ctx context.Context, code string) (*domain.Agency, error) {
	query := `SELECT id, code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, created_at, updated_at FROM agencies WHERE code = ?`
	row := r.db.QueryRowContext(ctx, query, code)

	var a domain.Agency
	var agencyType, crTs, upTs string
	err := row.Scan(&a.ID, &a.Code, &a.Name, &agencyType, &a.RegionCode, &a.ProvinceCode, &a.MuniCode, &a.BarangayCode, &a.ContactNumber, &a.Latitude, &a.Longitude, &crTs, &upTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}
	a.Type = domain.AgencyType(agencyType)
	a.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	a.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &a, nil
}

func (r *sqliteAgencyRepo) ListAgencies(ctx context.Context) ([]*domain.Agency, error) {
	query := `SELECT id, code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, created_at, updated_at FROM agencies ORDER BY name ASC`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var agencies []*domain.Agency
	for rows.Next() {
		var a domain.Agency
		var agencyType, crTs, upTs string
		if err := rows.Scan(&a.ID, &a.Code, &a.Name, &agencyType, &a.RegionCode, &a.ProvinceCode, &a.MuniCode, &a.BarangayCode, &a.ContactNumber, &a.Latitude, &a.Longitude, &crTs, &upTs); err != nil {
			return nil, err
		}
		a.Type = domain.AgencyType(agencyType)
		a.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		a.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
		agencies = append(agencies, &a)
	}
	return agencies, nil
}

func (r *sqliteAgencyRepo) UpdateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	query := `
		UPDATE agencies
		SET code=?, name=?, type=?, region_code=?, province_code=?, muni_code=?, barangay_code=?, contact_number=?, latitude=?, longitude=?, updated_at=?
		WHERE id=?
	`
	res, err := r.db.ExecContext(ctx, query,
		agency.Code, agency.Name, string(agency.Type), agency.RegionCode, agency.ProvinceCode,
		agency.MuniCode, agency.BarangayCode, agency.ContactNumber, agency.Latitude, agency.Longitude,
		nowStr, agency.ID,
	)
	if err != nil {
		return nil, err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return nil, domain.ErrNotFound
	}
	agency.UpdatedAt = now
	return agency, nil
}

func (r *sqliteAgencyRepo) DeleteAgency(ctx context.Context, id int64) error {
	query := `DELETE FROM agencies WHERE id=?`
	res, err := r.db.ExecContext(ctx, query, id)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

// ResponderUnit CRUD
func (r *sqliteAgencyRepo) CreateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	if responder.Status == "" {
		responder.Status = "IDLE"
	}

	query := `
		INSERT INTO responder_units (agency_id, target_id, unit_callsign, device_id, status, current_lat, current_lon, last_seen_at, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query,
		responder.AgencyID, responder.TargetID, responder.UnitCallsign, responder.DeviceID,
		responder.Status, responder.CurrentLat, responder.CurrentLon, nowStr, nowStr, nowStr,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create responder: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	responder.ID = id
	responder.LastSeenAt = now
	responder.CreatedAt = now
	responder.UpdatedAt = now
	return responder, nil
}

func (r *sqliteAgencyRepo) GetResponderByID(ctx context.Context, id int64) (*domain.ResponderUnit, error) {
	query := `SELECT id, agency_id, target_id, unit_callsign, device_id, status, current_lat, current_lon, last_seen_at, created_at, updated_at FROM responder_units WHERE id=?`
	row := r.db.QueryRowContext(ctx, query, id)

	var u domain.ResponderUnit
	var lsTs, crTs, upTs string
	err := row.Scan(&u.ID, &u.AgencyID, &u.TargetID, &u.UnitCallsign, &u.DeviceID, &u.Status, &u.CurrentLat, &u.CurrentLon, &lsTs, &crTs, &upTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}
	u.LastSeenAt, _ = time.Parse(time.RFC3339Nano, lsTs)
	u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &u, nil
}

func (r *sqliteAgencyRepo) ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error) {
	var query string
	var args []interface{}

	if agencyID != nil {
		query = `SELECT id, agency_id, target_id, unit_callsign, device_id, status, current_lat, current_lon, last_seen_at, created_at, updated_at FROM responder_units WHERE agency_id=? ORDER BY unit_callsign ASC`
		args = append(args, *agencyID)
	} else {
		query = `SELECT id, agency_id, target_id, unit_callsign, device_id, status, current_lat, current_lon, last_seen_at, created_at, updated_at FROM responder_units ORDER BY unit_callsign ASC`
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var responders []*domain.ResponderUnit
	for rows.Next() {
		var u domain.ResponderUnit
		var lsTs, crTs, upTs string
		if err := rows.Scan(&u.ID, &u.AgencyID, &u.TargetID, &u.UnitCallsign, &u.DeviceID, &u.Status, &u.CurrentLat, &u.CurrentLon, &lsTs, &crTs, &upTs); err != nil {
			return nil, err
		}
		u.LastSeenAt, _ = time.Parse(time.RFC3339Nano, lsTs)
		u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
		responders = append(responders, &u)
	}
	return responders, nil
}

func (r *sqliteAgencyRepo) UpdateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	query := `
		UPDATE responder_units
		SET agency_id=?, target_id=?, unit_callsign=?, device_id=?, status=?, current_lat=?, current_lon=?, updated_at=?
		WHERE id=?
	`
	res, err := r.db.ExecContext(ctx, query,
		responder.AgencyID, responder.TargetID, responder.UnitCallsign, responder.DeviceID,
		responder.Status, responder.CurrentLat, responder.CurrentLon, nowStr, responder.ID,
	)
	if err != nil {
		return nil, err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return nil, domain.ErrNotFound
	}
	responder.UpdatedAt = now
	return responder, nil
}

func (r *sqliteAgencyRepo) UpdateResponderLocation(ctx context.Context, id int64, lat, lon float64, status string) error {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	query := `UPDATE responder_units SET current_lat=?, current_lon=?, status=?, last_seen_at=?, updated_at=? WHERE id=?`
	res, err := r.db.ExecContext(ctx, query, lat, lon, status, nowStr, nowStr, id)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteAgencyRepo) DeleteResponder(ctx context.Context, id int64) error {
	query := `DELETE FROM responder_units WHERE id=?`
	res, err := r.db.ExecContext(ctx, query, id)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}
