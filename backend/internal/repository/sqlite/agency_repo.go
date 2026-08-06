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

	isActiveInt := 0
	if agency.IsActive {
		isActiveInt = 1
	}

	query := `
		INSERT INTO agencies (code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, is_active, operating_hours, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query,
		agency.Code, agency.Name, string(agency.Type), agency.RegionCode, agency.ProvinceCode,
		agency.MuniCode, agency.BarangayCode, agency.ContactNumber, agency.Latitude, agency.Longitude,
		isActiveInt, agency.OperatingHours, nowStr, nowStr,
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
	query := `SELECT id, code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, is_active, operating_hours, created_at, updated_at FROM agencies WHERE id = ?`
	row := r.db.QueryRowContext(ctx, query, id)

	var a domain.Agency
	var agencyType, crTs, upTs string
	var isActiveInt int
	err := row.Scan(&a.ID, &a.Code, &a.Name, &agencyType, &a.RegionCode, &a.ProvinceCode, &a.MuniCode, &a.BarangayCode, &a.ContactNumber, &a.Latitude, &a.Longitude, &isActiveInt, &a.OperatingHours, &crTs, &upTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}
	a.Type = domain.AgencyType(agencyType)
	a.IsActive = isActiveInt == 1
	a.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	a.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &a, nil
}

func (r *sqliteAgencyRepo) GetAgencyByCode(ctx context.Context, code string) (*domain.Agency, error) {
	query := `SELECT id, code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, is_active, operating_hours, created_at, updated_at FROM agencies WHERE code = ?`
	row := r.db.QueryRowContext(ctx, query, code)

	var a domain.Agency
	var agencyType, crTs, upTs string
	var isActiveInt int
	err := row.Scan(&a.ID, &a.Code, &a.Name, &agencyType, &a.RegionCode, &a.ProvinceCode, &a.MuniCode, &a.BarangayCode, &a.ContactNumber, &a.Latitude, &a.Longitude, &isActiveInt, &a.OperatingHours, &crTs, &upTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}
	a.Type = domain.AgencyType(agencyType)
	a.IsActive = isActiveInt == 1
	a.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	a.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &a, nil
}

func (r *sqliteAgencyRepo) ListAgencies(ctx context.Context) ([]*domain.Agency, error) {
	query := `SELECT id, code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude, is_active, operating_hours, created_at, updated_at FROM agencies ORDER BY name ASC`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var agencies []*domain.Agency
	for rows.Next() {
		var a domain.Agency
		var agencyType, crTs, upTs string
		var isActiveInt int
		if err := rows.Scan(&a.ID, &a.Code, &a.Name, &agencyType, &a.RegionCode, &a.ProvinceCode, &a.MuniCode, &a.BarangayCode, &a.ContactNumber, &a.Latitude, &a.Longitude, &isActiveInt, &a.OperatingHours, &crTs, &upTs); err != nil {
			return nil, err
		}
		a.Type = domain.AgencyType(agencyType)
		a.IsActive = isActiveInt == 1
		a.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		a.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
		agencies = append(agencies, &a)
	}
	return agencies, nil
}

func (r *sqliteAgencyRepo) UpdateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	isActiveInt := 0
	if agency.IsActive {
		isActiveInt = 1
	}

	query := `
		UPDATE agencies
		SET code=?, name=?, type=?, region_code=?, province_code=?, muni_code=?, barangay_code=?, contact_number=?, latitude=?, longitude=?, is_active=?, operating_hours=?, updated_at=?
		WHERE id=?
	`
	res, err := r.db.ExecContext(ctx, query,
		agency.Code, agency.Name, string(agency.Type), agency.RegionCode, agency.ProvinceCode,
		agency.MuniCode, agency.BarangayCode, agency.ContactNumber, agency.Latitude, agency.Longitude,
		isActiveInt, agency.OperatingHours, nowStr, agency.ID,
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
		INSERT INTO responder_units (agency_id, user_id, target_id, unit_callsign, responder_name, responder_phone, device_id, status, current_lat, current_lon, last_seen_at, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query,
		responder.AgencyID, responder.UserID, responder.TargetID, responder.UnitCallsign, responder.ResponderName, responder.ResponderPhone, responder.DeviceID,
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
	query := `
		SELECT r.id, r.agency_id, r.user_id, r.target_id, r.unit_callsign, r.responder_name, r.responder_phone, r.device_id, r.status, r.current_lat, r.current_lon, r.last_seen_at, r.created_at, r.updated_at,
		       u.name, u.phone_number
		FROM responder_units r
		LEFT JOIN users u ON r.user_id = u.id
		WHERE r.id=?
	`
	row := r.db.QueryRowContext(ctx, query, id)

	var u domain.ResponderUnit
	var lsTs, crTs, upTs string
	var userID sql.NullInt64
	var uName, uPhone sql.NullString

	err := row.Scan(&u.ID, &u.AgencyID, &userID, &u.TargetID, &u.UnitCallsign, &u.ResponderName, &u.ResponderPhone, &u.DeviceID, &u.Status, &u.CurrentLat, &u.CurrentLon, &lsTs, &crTs, &upTs, &uName, &uPhone)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	if userID.Valid {
		u.UserID = &userID.Int64
	}
	if u.ResponderName == "" && uName.Valid {
		u.ResponderName = uName.String
	}
	if u.ResponderPhone == "" && uPhone.Valid {
		u.ResponderPhone = uPhone.String
	}

	u.LastSeenAt, _ = time.Parse(time.RFC3339Nano, lsTs)
	u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &u, nil
}

func (r *sqliteAgencyRepo) GetResponderByUserID(ctx context.Context, userID int64) (*domain.ResponderUnit, error) {
	query := `
		SELECT r.id, r.agency_id, r.user_id, r.target_id, r.unit_callsign, r.responder_name, r.responder_phone, r.device_id, r.status, r.current_lat, r.current_lon, r.last_seen_at, r.created_at, r.updated_at,
		       u.name, u.phone_number
		FROM responder_units r
		LEFT JOIN users u ON r.user_id = u.id
		WHERE r.user_id=?
	`
	row := r.db.QueryRowContext(ctx, query, userID)

	var u domain.ResponderUnit
	var lsTs, crTs, upTs string
	var uID sql.NullInt64
	var uName, uPhone sql.NullString

	err := row.Scan(&u.ID, &u.AgencyID, &uID, &u.TargetID, &u.UnitCallsign, &u.ResponderName, &u.ResponderPhone, &u.DeviceID, &u.Status, &u.CurrentLat, &u.CurrentLon, &lsTs, &crTs, &upTs, &uName, &uPhone)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	if uID.Valid {
		u.UserID = &uID.Int64
	}
	if u.ResponderName == "" && uName.Valid {
		u.ResponderName = uName.String
	}
	if u.ResponderPhone == "" && uPhone.Valid {
		u.ResponderPhone = uPhone.String
	}

	u.LastSeenAt, _ = time.Parse(time.RFC3339Nano, lsTs)
	u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)

	return &u, nil
}


func (r *sqliteAgencyRepo) ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error) {
	var query string
	var args []interface{}

	baseQuery := `
		SELECT r.id, r.agency_id, r.user_id, r.target_id, r.unit_callsign, r.responder_name, r.responder_phone, r.device_id, r.status, r.current_lat, r.current_lon, r.last_seen_at, r.created_at, r.updated_at,
		       u.name, u.phone_number
		FROM responder_units r
		LEFT JOIN users u ON r.user_id = u.id
	`

	if agencyID != nil {
		query = baseQuery + ` WHERE r.agency_id=? ORDER BY r.unit_callsign ASC`
		args = append(args, *agencyID)
	} else {
		query = baseQuery + ` ORDER BY r.unit_callsign ASC`
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
		var userID sql.NullInt64
		var uName, uPhone sql.NullString

		if err := rows.Scan(&u.ID, &u.AgencyID, &userID, &u.TargetID, &u.UnitCallsign, &u.ResponderName, &u.ResponderPhone, &u.DeviceID, &u.Status, &u.CurrentLat, &u.CurrentLon, &lsTs, &crTs, &upTs, &uName, &uPhone); err != nil {
			return nil, err
		}

		if userID.Valid {
			u.UserID = &userID.Int64
		}
		if u.ResponderName == "" && uName.Valid {
			u.ResponderName = uName.String
		}
		if u.ResponderPhone == "" && uPhone.Valid {
			u.ResponderPhone = uPhone.String
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
		SET agency_id=?, user_id=?, target_id=?, unit_callsign=?, responder_name=?, responder_phone=?, device_id=?, status=?, current_lat=?, current_lon=?, updated_at=?
		WHERE id=?
	`
	res, err := r.db.ExecContext(ctx, query,
		responder.AgencyID, responder.UserID, responder.TargetID, responder.UnitCallsign, responder.ResponderName, responder.ResponderPhone, responder.DeviceID,
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
