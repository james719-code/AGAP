package repository

import (
	"context"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type IncidentFilter struct {
	IncidentType string
	Status       string
	RegionCode   string
	Since        *time.Time
	Limit        int
	Offset       int
}

type TelemetryRepository interface {
	SaveIncident(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error)
	AppendHopEvent(ctx context.Context, packetID string, hopCount int, senderID string) error
	GetIncidentByID(ctx context.Context, id int64) (*domain.Incident, error)
	GetIncidentByPacketID(ctx context.Context, packetID string) (*domain.Incident, error)
	ListIncidents(ctx context.Context, filter IncidentFilter) ([]*domain.Incident, int, error)
	UpdateIncidentStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus) error
	Ping(ctx context.Context) error
	Close() error
}

