package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type reportRepository struct {
	db *sql.DB
}

func NewReportRepository(db *sql.DB) repository.ReportRepository {
	return &reportRepository{db: db}
}

func (r *reportRepository) CreateReport(ctx context.Context, report *domain.IncidentReport) error {
	query := `
		INSERT INTO incident_reports (
			report_uuid, transport_token, incident_id, reporter_user_id, reporter_device_id,
			gateway_device_id, submission_type, subject_relation, emergency_type, subtype,
			description, people_affected, immediate_danger, location_source, selected_barangay_code,
			latitude, longitude, gps_accuracy_meters, observed_at, origin_channel, detail_state
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query,
		report.ReportUUID, report.TransportToken, report.IncidentID, report.ReporterUserID, report.ReporterDeviceID,
		report.GatewayDeviceID, report.SubmissionType, report.SubjectRelation, report.EmergencyType, report.Subtype,
		report.Description, report.PeopleAffected, report.ImmediateDanger, report.LocationSource, report.SelectedBarangayCode,
		report.Latitude, report.Longitude, report.GPSAccuracyMeters, report.ObservedAt.Format(time.RFC3339), report.OriginChannel, report.DetailState,
	)
	if err != nil {
		return err
	}
	id, err := res.LastInsertId()
	if err == nil {
		report.ID = id
	}
	return nil
}

func (r *reportRepository) GetReportByUUID(ctx context.Context, uuid string) (*domain.IncidentReport, error) {
	query := `
		SELECT id, report_uuid, transport_token, incident_id, reporter_user_id, reporter_device_id,
		       gateway_device_id, submission_type, subject_relation, emergency_type, subtype,
		       description, people_affected, immediate_danger, location_source, selected_barangay_code,
		       latitude, longitude, gps_accuracy_meters, observed_at, origin_channel, detail_state, created_at
		FROM incident_reports WHERE report_uuid = ?
	`
	return r.scanReport(r.db.QueryRowContext(ctx, query, uuid))
}

func (r *reportRepository) GetReportByToken(ctx context.Context, token string) (*domain.IncidentReport, error) {
	query := `
		SELECT id, report_uuid, transport_token, incident_id, reporter_user_id, reporter_device_id,
		       gateway_device_id, submission_type, subject_relation, emergency_type, subtype,
		       description, people_affected, immediate_danger, location_source, selected_barangay_code,
		       latitude, longitude, gps_accuracy_meters, observed_at, origin_channel, detail_state, created_at
		FROM incident_reports WHERE transport_token = ?
	`
	return r.scanReport(r.db.QueryRowContext(ctx, query, token))
}

func (r *reportRepository) GetReportsByReporterUserID(ctx context.Context, userID int64) ([]*domain.IncidentReport, error) {
	query := `
		SELECT id, report_uuid, transport_token, incident_id, reporter_user_id, reporter_device_id,
		       gateway_device_id, submission_type, subject_relation, emergency_type, subtype,
		       description, people_affected, immediate_danger, location_source, selected_barangay_code,
		       latitude, longitude, gps_accuracy_meters, observed_at, origin_channel, detail_state, created_at
		FROM incident_reports WHERE reporter_user_id = ? ORDER BY created_at DESC
	`
	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var reports []*domain.IncidentReport
	for rows.Next() {
		rep, err := r.scanReportRow(rows)
		if err != nil {
			return nil, err
		}
		reports = append(reports, rep)
	}
	return reports, nil
}

func (r *reportRepository) GetReportsByIncidentID(ctx context.Context, incidentID int64) ([]*domain.IncidentReport, error) {
	query := `
		SELECT id, report_uuid, transport_token, incident_id, reporter_user_id, reporter_device_id,
		       gateway_device_id, submission_type, subject_relation, emergency_type, subtype,
		       description, people_affected, immediate_danger, location_source, selected_barangay_code,
		       latitude, longitude, gps_accuracy_meters, observed_at, origin_channel, detail_state, created_at
		FROM incident_reports WHERE incident_id = ? ORDER BY created_at DESC
	`
	rows, err := r.db.QueryContext(ctx, query, incidentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var reports []*domain.IncidentReport
	for rows.Next() {
		rep, err := r.scanReportRow(rows)
		if err != nil {
			return nil, err
		}
		reports = append(reports, rep)
	}
	return reports, nil
}

func (r *reportRepository) EnrichReport(ctx context.Context, report *domain.IncidentReport) error {
	query := `
		UPDATE incident_reports SET
			reporter_user_id = ?, description = ?, subtype = ?, subject_relation = ?,
			people_affected = ?, immediate_danger = ?, detail_state = 'FULL'
		WHERE transport_token = ?
	`
	_, err := r.db.ExecContext(ctx, query,
		report.ReporterUserID, report.Description, report.Subtype, report.SubjectRelation,
		report.PeopleAffected, report.ImmediateDanger, report.TransportToken,
	)
	return err
}

func (r *reportRepository) scanReport(row *sql.Row) (*domain.IncidentReport, error) {
	var rep domain.IncidentReport
	var observedAtStr, createdAtStr string
	err := row.Scan(
		&rep.ID, &rep.ReportUUID, &rep.TransportToken, &rep.IncidentID, &rep.ReporterUserID, &rep.ReporterDeviceID,
		&rep.GatewayDeviceID, &rep.SubmissionType, &rep.SubjectRelation, &rep.EmergencyType, &rep.Subtype,
		&rep.Description, &rep.PeopleAffected, &rep.ImmediateDanger, &rep.LocationSource, &rep.SelectedBarangayCode,
		&rep.Latitude, &rep.Longitude, &rep.GPSAccuracyMeters, &observedAtStr, &rep.OriginChannel, &rep.DetailState, &createdAtStr,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	rep.ObservedAt, _ = time.Parse(time.RFC3339, observedAtStr)
	rep.CreatedAt, _ = time.Parse(time.RFC3339, createdAtStr)
	return &rep, nil
}

func (r *reportRepository) scanReportRow(rows *sql.Rows) (*domain.IncidentReport, error) {
	var rep domain.IncidentReport
	var observedAtStr, createdAtStr string
	err := rows.Scan(
		&rep.ID, &rep.ReportUUID, &rep.TransportToken, &rep.IncidentID, &rep.ReporterUserID, &rep.ReporterDeviceID,
		&rep.GatewayDeviceID, &rep.SubmissionType, &rep.SubjectRelation, &rep.EmergencyType, &rep.Subtype,
		&rep.Description, &rep.PeopleAffected, &rep.ImmediateDanger, &rep.LocationSource, &rep.SelectedBarangayCode,
		&rep.Latitude, &rep.Longitude, &rep.GPSAccuracyMeters, &observedAtStr, &rep.OriginChannel, &rep.DetailState, &createdAtStr,
	)
	if err != nil {
		return nil, err
	}
	rep.ObservedAt, _ = time.Parse(time.RFC3339, observedAtStr)
	rep.CreatedAt, _ = time.Parse(time.RFC3339, createdAtStr)
	return &rep, nil
}
