package database

import (
	"database/sql"
	"fmt"

	"github.com/travelingsalesman/agap/backend/internal/config"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

type Container struct {
	DB            *sql.DB
	TelemetryRepo repository.TelemetryRepository
	AgencyRepo    repository.AgencyRepository
	UserRepo      repository.UserRepository
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

		return &Container{
			DB:            db,
			TelemetryRepo: telemetryRepo,
			AgencyRepo:    agencyRepo,
			UserRepo:      userRepo,
		}, nil

	case "postgres":
		// Production PostgreSQL driver initialization goes here.
		// Returning an explicit error until PG driver is added.
		return nil, fmt.Errorf("postgres support is planned for production release — set DB_TYPE=sqlite for development")

	default:
		return nil, fmt.Errorf("unsupported DB_TYPE: %q", cfg.DBType)
	}
}
