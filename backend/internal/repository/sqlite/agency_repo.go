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

	agencies := make([]*domain.Agency, 0)
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
		INSERT INTO rescue_units (agency_id, user_id, target_id, unit_code, responder_name, responder_phone, device_id, status, current_latitude, current_longitude, last_location_at, created_at, updated_at)
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
		SELECT r.id, r.agency_id, r.user_id, r.target_id, r.unit_code, r.responder_name, r.responder_phone, r.device_id, r.status, r.current_latitude, r.current_longitude, r.last_location_at, r.created_at, r.updated_at,
		       u.name, u.phone_number
		FROM rescue_units r
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
		SELECT r.id, r.agency_id, r.user_id, r.target_id, r.unit_code, r.responder_name, r.responder_phone, r.device_id, r.status, r.current_latitude, r.current_longitude, r.last_location_at, r.created_at, r.updated_at,
		       u.name, u.phone_number
		FROM rescue_units r
		LEFT JOIN users u ON r.user_id = u.id
		WHERE r.user_id = ?
		   OR EXISTS (
			SELECT 1 FROM rescue_unit_members rum
			WHERE rum.rescue_unit_id = r.id
			  AND rum.responder_id = ?
			  AND rum.removed_at IS NULL
		   )
		ORDER BY CASE WHEN r.user_id = ? THEN 0 ELSE 1 END, r.id ASC
		LIMIT 1
	`
	row := r.db.QueryRowContext(ctx, query, userID, userID, userID)

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
		SELECT r.id, r.agency_id, r.user_id, r.target_id, r.unit_code, r.responder_name, r.responder_phone, r.device_id, r.status, r.current_latitude, r.current_longitude, r.last_location_at, r.created_at, r.updated_at,
		       u.name, u.phone_number
		FROM rescue_units r
		LEFT JOIN users u ON r.user_id = u.id
	`

	if agencyID != nil {
		query = baseQuery + ` WHERE r.agency_id=? ORDER BY r.unit_code ASC`
		args = append(args, *agencyID)
	} else {
		query = baseQuery + ` ORDER BY r.unit_code ASC`
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	responders := make([]*domain.ResponderUnit, 0)
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
		UPDATE rescue_units
		SET agency_id=?, user_id=?, target_id=?, unit_code=?, responder_name=?, responder_phone=?, device_id=?, status=?, current_latitude=?, current_longitude=?, updated_at=?
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

	query := `UPDATE rescue_units SET current_latitude=?, current_longitude=?, status=?, last_location_at=?, updated_at=? WHERE id=?`
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
	query := `DELETE FROM rescue_units WHERE id=?`
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

func (r *sqliteAgencyRepo) AddRescueUnitMember(ctx context.Context, member *domain.RescueUnitMember) (*domain.RescueUnitMember, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)
	res, err := r.db.ExecContext(ctx, `
		INSERT INTO rescue_unit_members (rescue_unit_id, responder_id, is_leader, assigned_at)
		VALUES (?, ?, ?, ?)
	`, member.RescueUnitID, member.ResponderID, boolToInt(member.IsLeader), nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to add rescue unit member: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}
	member.ID = id
	member.AssignedAt = now
	return member, nil
}

func (r *sqliteAgencyRepo) ListRescueUnitMembers(ctx context.Context, rescueUnitID int64) ([]*domain.RescueUnitMember, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, rescue_unit_id, responder_id, is_leader, assigned_at, removed_at
		FROM rescue_unit_members
		WHERE rescue_unit_id = ?
		ORDER BY removed_at IS NOT NULL, is_leader DESC, assigned_at ASC
	`, rescueUnitID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	members := make([]*domain.RescueUnitMember, 0)
	for rows.Next() {
		var member domain.RescueUnitMember
		var isLeader int
		var assignedAt string
		var removedAt sql.NullString
		if err := rows.Scan(&member.ID, &member.RescueUnitID, &member.ResponderID, &isLeader, &assignedAt, &removedAt); err != nil {
			return nil, err
		}
		member.IsLeader = isLeader == 1
		member.AssignedAt, _ = time.Parse(time.RFC3339Nano, assignedAt)
		if removedAt.Valid && removedAt.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, removedAt.String)
			member.RemovedAt = &t
		}
		members = append(members, &member)
	}
	return members, rows.Err()
}

func (r *sqliteAgencyRepo) RemoveRescueUnitMember(ctx context.Context, rescueUnitID int64, responderID int64) error {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	res, err := r.db.ExecContext(ctx, `
		UPDATE rescue_unit_members
		SET removed_at = ?
		WHERE rescue_unit_id = ? AND responder_id = ? AND removed_at IS NULL
	`, nowStr, rescueUnitID, responderID)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteAgencyRepo) RouteIncidentAgencies(ctx context.Context, incidentID int64, emergencyType string, barangayCode string) error {
	queryTypes := `
		SELECT eta.agency_type_id, eta.is_primary
		FROM emergency_type_agency_types eta
		JOIN emergency_types et ON et.id = eta.emergency_type_id
		WHERE et.code = ? OR et.name = ?
	`
	rows, err := r.db.QueryContext(ctx, queryTypes, emergencyType, emergencyType)
	if err != nil {
		return err
	}
	defer rows.Close()

	type agencyTypePair struct {
		typeID    int64
		isPrimary bool
	}
	var pairs []agencyTypePair
	for rows.Next() {
		var p agencyTypePair
		var isPrimaryInt int
		if err := rows.Scan(&p.typeID, &isPrimaryInt); err != nil {
			return err
		}
		p.isPrimary = isPrimaryInt == 1
		pairs = append(pairs, p)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	rows.Close()

	routedCount := 0
	for _, p := range pairs {
		queryAgency := `
			SELECT a.id FROM agencies a
			JOIN agency_service_areas asa ON asa.agency_id = a.id
			WHERE a.type = (SELECT code FROM agency_types WHERE id = ?)
			  AND (asa.barangay_code = ? OR asa.barangay_code = 'ALL' OR ? = '')
		`
		var agencyID int64
		err := r.db.QueryRowContext(ctx, queryAgency, p.typeID, barangayCode, barangayCode).Scan(&agencyID)
		if errors.Is(err, sql.ErrNoRows) {
			continue
		}
		if err != nil {
			return err
		}
		role := "PRIMARY"
		if !p.isPrimary {
			role = "SUPPORT"
		}
		if _, err := r.db.ExecContext(ctx, `INSERT OR IGNORE INTO incident_agencies (incident_id, agency_id, role) VALUES (?, ?, ?)`, incidentID, agencyID, role); err != nil {
			return err
		}
		routedCount++
	}

	if routedCount == 0 {
		var drrmoAgencyID int64
		err := r.db.QueryRowContext(ctx, `SELECT id FROM agencies WHERE code = 'DRRMO' OR type = 'DRRMO' LIMIT 1`).Scan(&drrmoAgencyID)
		if errors.Is(err, sql.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		if _, err := r.db.ExecContext(ctx, `INSERT OR IGNORE INTO incident_agencies (incident_id, agency_id, role) VALUES (?, ?, 'PRIMARY')`, incidentID, drrmoAgencyID); err != nil {
			return err
		}
	}

	return nil
}
