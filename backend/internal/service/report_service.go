package service

import (
	"context"
	"errors"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

var ErrReportNotFound = errors.New("report not found")

type ReportService interface {
	CreateOrEnrichReport(ctx context.Context, req *domain.CreateReportRequest, userID *int64) (*domain.IncidentReport, error)
	GetReportByUUID(ctx context.Context, uuid string) (*domain.IncidentReport, error)
	GetUserReports(ctx context.Context, userID int64) ([]*domain.IncidentReport, error)
	GetIncidentReports(ctx context.Context, incidentID int64) ([]*domain.IncidentReport, error)
}

type reportService struct {
	reportRepo   repository.ReportRepository
	agencyRepo   repository.AgencyRepository
	telemetrySvc TelemetryService
}

func NewReportService(reportRepo repository.ReportRepository, agencyRepo repository.AgencyRepository, telemetrySvc TelemetryService) ReportService {
	return &reportService{
		reportRepo:   reportRepo,
		agencyRepo:   agencyRepo,
		telemetrySvc: telemetrySvc,
	}
}

func (s *reportService) CreateOrEnrichReport(ctx context.Context, req *domain.CreateReportRequest, userID *int64) (*domain.IncidentReport, error) {
	var existing *domain.IncidentReport
	var err error

	if req.ReportUUID != "" {
		existing, err = s.reportRepo.GetReportByUUID(ctx, req.ReportUUID)
	}
	if (existing == nil || err != nil) && req.TransportToken != "" {
		existing, err = s.reportRepo.GetReportByToken(ctx, req.TransportToken)
	}

	if err == nil && existing != nil {
		// A report UUID / transport token is an idempotency key, not an authorization
		// credential. Never allow one citizen to claim or enrich another citizen's report.
		if existing.ReporterUserID != nil && userID != nil && *existing.ReporterUserID != *userID {
			return nil, domain.ErrUnauthorized
		}
		// Gateway/admin retransmissions may be idempotently acknowledged, but they
		// cannot overwrite details on a report already owned by a citizen.
		if existing.ReporterUserID != nil && userID == nil {
			return existing, nil
		}
		if existing.DetailState == "MINIMAL" && req.Description != "" {
			existing.ReporterUserID = userID
			existing.ReporterDeviceID = req.ReporterDeviceID
			existing.Description = req.Description
			existing.Subtype = req.Subtype
			existing.SubjectRelation = req.SubjectRelation
			existing.PeopleAffected = req.PeopleAffected
			existing.ImmediateDanger = req.ImmediateDanger
			existing.DetailState = "FULL"

			if err := s.reportRepo.EnrichReport(ctx, existing); err != nil {
				return nil, err
			}
		}
		return existing, nil
	}

	detailState := "FULL"
	if req.Description == "" && req.GatewayDeviceID != "" {
		detailState = "MINIMAL"
	}

	report := &domain.IncidentReport{
		ReportUUID:           req.ReportUUID,
		TransportToken:       req.TransportToken,
		ReporterUserID:       userID,
		ReporterDeviceID:     req.ReporterDeviceID,
		GatewayDeviceID:      req.GatewayDeviceID,
		SubmissionType:       req.SubmissionType,
		SubjectRelation:      req.SubjectRelation,
		EmergencyType:        req.EmergencyType,
		Subtype:              req.Subtype,
		Description:          req.Description,
		PeopleAffected:       req.PeopleAffected,
		ImmediateDanger:      req.ImmediateDanger,
		LocationSource:       req.LocationSource,
		SelectedBarangayCode: req.SelectedBarangayCode,
		Latitude:             req.Latitude,
		Longitude:            req.Longitude,
		GPSAccuracyMeters:    req.GPSAccuracyMeters,
		ObservedAt:           time.Now(),
		OriginChannel:        req.OriginChannel,
		DetailState:          detailState,
		CreatedAt:            time.Now(),
	}

	if report.SubmissionType == "" {
		report.SubmissionType = "CITIZEN_REPORT"
	}
	if report.OriginChannel == "" {
		report.OriginChannel = "ONLINE"
	}

	lat := 0.0
	if report.Latitude != nil {
		lat = *report.Latitude
	}
	lon := 0.0
	if report.Longitude != nil {
		lon = *report.Longitude
	}
	acc := 0.0
	if report.GPSAccuracyMeters != nil {
		acc = *report.GPSAccuracyMeters
	}

	senderDeviceID := report.ReporterDeviceID
	if senderDeviceID == "" {
		senderDeviceID = report.GatewayDeviceID
	}

	packet := &domain.TelemetryPacket{
		IncidentUUID:   report.ReportUUID,
		PacketUUID:     report.ReportUUID,
		PacketHash:     report.TransportToken,
		TargetID:       domain.TargetID{Raw: "BLE_P2P"},
		IncidentType:   domain.IncidentType(report.EmergencyType),
		Channel:        domain.ChannelType(report.OriginChannel),
		DeliveryPath:   domain.DeliveryDirectUplink,
		Latitude:       lat,
		Longitude:      lon,
		GPSAccuracy:    acc,
		SenderDeviceID: senderDeviceID,
		Timestamp:      report.ObservedAt,
	}

	incident, _, err := s.telemetrySvc.IngestPacket(ctx, packet)
	if err != nil {
		return nil, err
	}
	if incident == nil {
		return nil, errors.New("telemetry ingest returned no incident")
	}
	report.IncidentID = &incident.ID
	if s.agencyRepo != nil {
		if err := s.agencyRepo.RouteIncidentAgencies(ctx, incident.ID, report.EmergencyType, report.SelectedBarangayCode); err != nil {
			return nil, err
		}
	}

	if err := s.reportRepo.CreateReport(ctx, report); err != nil {
		return nil, err
	}

	return report, nil
}

func (s *reportService) GetReportByUUID(ctx context.Context, uuid string) (*domain.IncidentReport, error) {
	rep, err := s.reportRepo.GetReportByUUID(ctx, uuid)
	if err != nil {
		return nil, err
	}
	if rep == nil {
		return nil, ErrReportNotFound
	}
	return rep, nil
}

func (s *reportService) GetUserReports(ctx context.Context, userID int64) ([]*domain.IncidentReport, error) {
	return s.reportRepo.GetReportsByReporterUserID(ctx, userID)
}

func (s *reportService) GetIncidentReports(ctx context.Context, incidentID int64) ([]*domain.IncidentReport, error) {
	return s.reportRepo.GetReportsByIncidentID(ctx, incidentID)
}
