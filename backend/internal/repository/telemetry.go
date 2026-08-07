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
	RecordMeshDeliveryEvent(ctx context.Context, event *domain.MeshDeliveryEvent) error
	GetIncidentByID(ctx context.Context, id int64) (*domain.Incident, error)
	GetIncidentByIncidentUUID(ctx context.Context, incidentUUID string) (*domain.Incident, error)
	GetIncidentByPacketUUID(ctx context.Context, packetUUID string) (*domain.Incident, error)
	GetIncidentByPacketHash(ctx context.Context, packetHash string) (*domain.Incident, error)
	ListIncidents(ctx context.Context, filter IncidentFilter) ([]*domain.Incident, int, error)
	UpdateIncidentStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus) error
	AppendIncidentStatusHistory(ctx context.Context, event *domain.IncidentStatusHistory) error
	ListIncidentStatusHistory(ctx context.Context, incidentID int64) ([]*domain.IncidentStatusHistory, error)
	Ping(ctx context.Context) error
	Close() error
}
