package service_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/platform/cache"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
	"github.com/travelingsalesman/agap/backend/internal/service"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

func TestReportService_CreateAndEnrichReport(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_report_service.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	reportRepo := sqlite.NewReportRepository(db)
	agencyRepo := sqlite.NewAgencyRepo(db)
	telemetryRepo := sqlite.NewTelemetryRepo(db)
	telemetrySvc := service.NewTelemetryService(telemetryRepo, nil, nil, cache.NewTTLCache(time.Minute), websocket.NewHub())
	reportSvc := service.NewReportService(reportRepo, agencyRepo, telemetrySvc)

	ctx := context.Background()

	// Seed test user to satisfy foreign key constraint on reporter_user_id
	_, err = db.ExecContext(ctx, `
		INSERT INTO users (id, username, password_hash, role, account_status, name, phone_number)
		VALUES (42, 'testreporter', 'hash', 'CITIZEN', 'APPROVED', 'Test Reporter', '09123456789')
	`)
	if err != nil {
		t.Fatalf("failed to seed test user: %v", err)
	}

	userID := int64(42)

	lat := 13.1391
	lon := 123.7438
	acc := 15.0

	// 1. Create initial minimal gateway report
	req := &domain.CreateReportRequest{
		ReportUUID:           "rep-uuid-001",
		TransportToken:       "ABCDEF",
		GatewayDeviceID:      "gateway-dev-01",
		SubmissionType:       "QUICK_SOS",
		SubjectRelation:      "SELF",
		EmergencyType:        "MEDICAL",
		LocationSource:       "GPS",
		SelectedBarangayCode: "PH-ALB-LEG-001",
		Latitude:             &lat,
		Longitude:            &lon,
		GPSAccuracyMeters:    &acc,
		OriginChannel:        "BLE_MESH",
	}

	created, err := reportSvc.CreateOrEnrichReport(ctx, req, &userID)
	if err != nil {
		t.Fatalf("unexpected error creating report: %v", err)
	}
	if created == nil || created.TransportToken != "ABCDEF" {
		t.Fatalf("expected created report with transport token ABCDEF, got %+v", created)
	}
	if created.DetailState != "MINIMAL" {
		t.Fatalf("expected MINIMAL detail state for gateway report without description, got %s", created.DetailState)
	}

	// 2. Enrich report with full detailed information
	enrichReq := &domain.CreateReportRequest{
		ReportUUID:           "rep-uuid-001",
		TransportToken:       "ABCDEF",
		GatewayDeviceID:      "gateway-dev-01",
		SubmissionType:       "DETAILED",
		SubjectRelation:      "FAMILY_MEMBER",
		EmergencyType:        "MEDICAL",
		Subtype:              "CARDIAC_ARREST",
		Description:          "Patient experiencing chest pain and shortness of breath.",
		PeopleAffected:       1,
		ImmediateDanger:      true,
		LocationSource:       "GPS",
		SelectedBarangayCode: "PH-ALB-LEG-001",
		Latitude:             &lat,
		Longitude:            &lon,
		GPSAccuracyMeters:    &acc,
		OriginChannel:        "CELLULAR",
	}

	enriched, err := reportSvc.CreateOrEnrichReport(ctx, enrichReq, &userID)
	if err != nil {
		t.Fatalf("unexpected error enriching report: %v", err)
	}
	if enriched.DetailState != "FULL" {
		t.Fatalf("expected FULL detail state after enrichment, got %s", enriched.DetailState)
	}
	if enriched.Description != "Patient experiencing chest pain and shortness of breath." {
		t.Fatalf("unexpected description: %s", enriched.Description)
	}

	// 3. Query user reports
	userReports, err := reportSvc.GetUserReports(ctx, userID)
	if err != nil {
		t.Fatalf("failed to fetch user reports: %v", err)
	}
	if len(userReports) == 0 {
		t.Fatalf("expected at least 1 user report, got 0")
	}

	// 4. Query incident reports
	if enriched.IncidentID != nil {
		incReports, err := reportSvc.GetIncidentReports(ctx, *enriched.IncidentID)
		if err != nil {
			t.Fatalf("failed to fetch incident reports: %v", err)
		}
		if len(incReports) == 0 {
			t.Fatalf("expected at least 1 report for incident ID %d", *enriched.IncidentID)
		}
	}
}
