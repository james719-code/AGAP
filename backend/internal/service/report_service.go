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
}

type reportService struct {
	reportRepo   repository.ReportRepository
	telemetrySvc TelemetryService
}

func NewReportService(reportRepo repository.ReportRepository, telemetrySvc TelemetryService) ReportService {
	return &reportService{
		reportRepo:   reportRepo,
		telemetrySvc: telemetrySvc,
	}
}

func (s *reportService) CreateOrEnrichReport(ctx context.Context, req *domain.CreateReportRequest, userID *int64) (*domain.IncidentReport, error) {
	if req.ReportUUID != "" {
		existing, err := s.reportRepo.GetReportByUUID(ctx, req.ReportUUID)
		if err == nil && existing != nil {
			return existing, nil
		}
	}

	if req.TransportToken != "" {
		existingByToken, err := s.reportRepo.GetReportByToken(ctx, req.TransportToken)
		if err == nil && existingByToken != nil {
			if existingByToken.DetailState == "MINIMAL" && req.Description != "" {
				existingByToken.ReporterUserID = userID
				existingByToken.Description = req.Description
				existingByToken.Subtype = req.Subtype
				existingByToken.SubjectRelation = req.SubjectRelation
				existingByToken.PeopleAffected = req.PeopleAffected
				existingByToken.ImmediateDanger = req.ImmediateDanger

				if err := s.reportRepo.EnrichReport(ctx, existingByToken); err != nil {
					return nil, err
				}
				return existingByToken, nil
			}
			return existingByToken, nil
		}
	}

	report := &domain.IncidentReport{
		ReportUUID:           req.ReportUUID,
		TransportToken:       req.TransportToken,
		ReporterUserID:       userID,
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
		DetailState:          "FULL",
		CreatedAt:            time.Now(),
	}

	if report.SubmissionType == "" {
		report.SubmissionType = "CITIZEN_REPORT"
	}
	if report.OriginChannel == "" {
		report.OriginChannel = "ONLINE"
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
