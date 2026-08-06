package sqlite

import (
	"context"
	"database/sql"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteGeoRepo struct {
	db *sql.DB
}

func NewGeoRepo(db *sql.DB) repository.GeoRepository {
	return &sqliteGeoRepo{db: db}
}

func (r *sqliteGeoRepo) ListProvinces(ctx context.Context) ([]*domain.Province, error) {
	query := `SELECT id, code, name, created_at FROM provinces ORDER BY name ASC`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.Province
	for rows.Next() {
		var p domain.Province
		var crTs string
		if err := rows.Scan(&p.ID, &p.Code, &p.Name, &crTs); err != nil {
			return nil, err
		}
		p.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		list = append(list, &p)
	}

	return list, nil
}

func (r *sqliteGeoRepo) ListMunicipalities(ctx context.Context, provinceID int64) ([]*domain.Municipality, error) {
	var query string
	var args []interface{}

	if provinceID > 0 {
		query = `SELECT id, province_id, code, name, created_at FROM municipalities WHERE province_id = ? ORDER BY name ASC`
		args = []interface{}{provinceID}
	} else {
		query = `SELECT id, province_id, code, name, created_at FROM municipalities ORDER BY name ASC`
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.Municipality
	for rows.Next() {
		var m domain.Municipality
		var crTs string
		if err := rows.Scan(&m.ID, &m.ProvinceID, &m.Code, &m.Name, &crTs); err != nil {
			return nil, err
		}
		m.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		list = append(list, &m)
	}

	return list, nil
}

func (r *sqliteGeoRepo) ListBarangays(ctx context.Context, municipalityID int64) ([]*domain.Barangay, error) {
	var query string
	var args []interface{}

	if municipalityID > 0 {
		query = `SELECT id, municipality_id, code, name, created_at FROM barangays WHERE municipality_id = ? ORDER BY name ASC`
		args = []interface{}{municipalityID}
	} else {
		query = `SELECT id, municipality_id, code, name, created_at FROM barangays ORDER BY name ASC`
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.Barangay
	for rows.Next() {
		var b domain.Barangay
		var crTs string
		if err := rows.Scan(&b.ID, &b.MunicipalityID, &b.Code, &b.Name, &crTs); err != nil {
			return nil, err
		}
		b.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		list = append(list, &b)
	}

	return list, nil
}

func (r *sqliteGeoRepo) ListAddresses(ctx context.Context, barangayID int64) ([]*domain.Address, error) {
	var query string
	var args []interface{}

	if barangayID > 0 {
		query = `SELECT id, barangay_id, street_address, latitude, longitude, created_at FROM addresses WHERE barangay_id = ? ORDER BY street_address ASC`
		args = []interface{}{barangayID}
	} else {
		query = `SELECT id, barangay_id, street_address, latitude, longitude, created_at FROM addresses ORDER BY street_address ASC`
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.Address
	for rows.Next() {
		var a domain.Address
		var crTs string
		if err := rows.Scan(&a.ID, &a.BarangayID, &a.StreetAddress, &a.Latitude, &a.Longitude, &crTs); err != nil {
			return nil, err
		}
		a.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		list = append(list, &a)
	}

	return list, nil
}
