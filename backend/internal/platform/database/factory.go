package database

import (
	"database/sql"
	"fmt"

	"github.com/travelingsalesman/agap/backend/internal/config"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

type Container struct {
	DB                *sql.DB
	TelemetryRepo     repository.TelemetryRepository
	AgencyRepo        repository.AgencyRepository
	UserRepo          repository.UserRepository
	DispatchRepo      repository.DispatchRepository
	NotificationRepo  repository.NotificationRepository
	AuditRepo         repository.AuditRepository
	GeoRepo           repository.GeoRepository
	MessageRepo       repository.MessageRepository
	EmergencyTypeRepo repository.EmergencyTypeRepository
	AnalyticsRepo     repository.AnalyticsRepository
}

func NewContainer(cfg *config.Config) (*Container, error) {
	switch cfg.DBType {
	case "sqlite":
		db, err := sqlite.Open(cfg.DBFilePath)
		if err != nil {
			return nil, fmt.Errorf("failed to initialize sqlite container: %w", err)
		}

		telemetryRepo := sqlite.NewTelemetryRepo(db)
		agencyRepo := sqlite.NewAgencyRepo(db)
		userRepo := sqlite.NewUserRepo(db)
		dispatchRepo := sqlite.NewDispatchRepo(db)
		notificationRepo := sqlite.NewNotificationRepo(db)
		auditRepo := sqlite.NewAuditRepo(db)
		geoRepo := sqlite.NewGeoRepo(db)
		messageRepo := sqlite.NewMessageRepo(db)
		emergencyTypeRepo := sqlite.NewEmergencyTypeRepo(db)
		analyticsRepo := sqlite.NewAnalyticsRepo(db)

		return &Container{
			DB:                db,
			TelemetryRepo:     telemetryRepo,
			AgencyRepo:        agencyRepo,
			UserRepo:          userRepo,
			DispatchRepo:      dispatchRepo,
			NotificationRepo:  notificationRepo,
			AuditRepo:         auditRepo,
			GeoRepo:           geoRepo,
			MessageRepo:       messageRepo,
			EmergencyTypeRepo: emergencyTypeRepo,
			AnalyticsRepo:     analyticsRepo,
		}, nil

	case "postgres":
		// Production PostgreSQL driver initialization goes here.
		// Returning an explicit error until PG driver is added.
		return nil, fmt.Errorf("postgres support is planned for production release — set DB_TYPE=sqlite for development")

	default:
		return nil, fmt.Errorf("unsupported DB_TYPE: %q", cfg.DBType)
	}
}
