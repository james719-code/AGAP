package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteTelemetryRepo struct {
	db *sql.DB
}

func NewTelemetryRepo(db *sql.DB) repository.TelemetryRepository {
	return &sqliteTelemetryRepo{db: db}
}

func (r *sqliteTelemetryRepo) SaveIncident(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error) {
	now := time.Now().UTC()
	eventTime := packet.Timestamp.UTC()
	if eventTime.IsZero() {
		eventTime = now
	}
	receivedAt := packet.ReceivedAt.UTC()
	if receivedAt.IsZero() {
		receivedAt = now
	}

	query := `
		INSERT INTO incidents (
			packet_id, packet_hash, target_id_raw, region_code, province_code,
			municipality_code, barangay_code, zone_code, incident_type, channel,
			delivery_path, latitude, longitude, altitude, gps_accuracy,
			hop_count, ttl, sender_device_id, status, event_timestamp, received_at,
			created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
	`

	res, err := r.db.ExecContext(
		ctx, query,
		packet.PacketID, packet.PacketHash, packet.TargetID.Raw,
		packet.TargetID.Region, packet.TargetID.Province, packet.TargetID.Municipality,
		packet.TargetID.Barangay, packet.TargetID.Zone, string(packet.IncidentType),
		string(packet.Channel), string(packet.DeliveryPath), packet.Latitude,
		packet.Longitude, packet.Altitude, packet.GPSAccuracy, packet.HopCount,
		packet.TTL, packet.SenderDeviceID, eventTime.Format(time.RFC3339Nano),
		receivedAt.Format(time.RFC3339Nano), now.Format(time.RFC3339Nano), now.Format(time.RFC3339Nano),
	)

	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE constraint failed") {
			// Duplicate packet! Let's fetch existing incident
			inc, fetchErr := r.GetIncidentByPacketID(ctx, packet.PacketID)
			if fetchErr == nil {
				return inc, true, nil
			}
			return nil, true, domain.ErrDuplicatePacket
		}
		return nil, false, fmt.Errorf("failed to save incident: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, false, fmt.Errorf("failed to get last insert id: %w", err)
	}

	inc := &domain.Incident{
		ID:             id,
		PacketID:       packet.PacketID,
		PacketHash:     packet.PacketHash,
		TargetID:       packet.TargetID,
		IncidentType:   packet.IncidentType,
		Channel:        packet.Channel,
		DeliveryPath:   packet.DeliveryPath,
		Latitude:       packet.Latitude,
		Longitude:      packet.Longitude,
		Altitude:       packet.Altitude,
		GPSAccuracy:    packet.GPSAccuracy,
		HopCount:       packet.HopCount,
		TTL:            packet.TTL,
		SenderDeviceID: packet.SenderDeviceID,
		Status:         domain.StatusActive,
		EventTimestamp: eventTime,
		ReceivedAt:     receivedAt,
		CreatedAt:      now,
		UpdatedAt:      now,
	}

	return inc, false, nil
}

func (r *sqliteTelemetryRepo) AppendHopEvent(ctx context.Context, packetID string, hopCount int, senderID string) error {
	query := `INSERT INTO hop_events (packet_id, hop_count, sender_device_id, recorded_at) VALUES (?, ?, ?, ?)`
	now := time.Now().UTC().Format(time.RFC3339Nano)
	_, err := r.db.ExecContext(ctx, query, packetID, hopCount, senderID, now)
	if err != nil {
		return fmt.Errorf("failed to append hop event: %w", err)
	}
	return nil
}

func (r *sqliteTelemetryRepo) GetIncidentByPacketID(ctx context.Context, packetID string) (*domain.Incident, error) {
	query := `
		SELECT id, packet_id, packet_hash, target_id_raw, incident_type, channel, delivery_path,
		       latitude, longitude, altitude, gps_accuracy, hop_count, ttl, sender_device_id,
		       status, event_timestamp, received_at, created_at, updated_at
		FROM incidents WHERE packet_id = ?
	`
	row := r.db.QueryRowContext(ctx, query, packetID)

	var inc domain.Incident
	var targetIDRaw, incType, channel, deliveryPath, status string
	var eventTs, recTs, crTs, upTs string

	err := row.Scan(
		&inc.ID, &inc.PacketID, &inc.PacketHash, &targetIDRaw, &incType, &channel, &deliveryPath,
		&inc.Latitude, &inc.Longitude, &inc.Altitude, &inc.GPSAccuracy, &inc.HopCount, &inc.TTL,
		&inc.SenderDeviceID, &status, &eventTs, &recTs, &crTs, &upTs,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, fmt.Errorf("failed to scan incident: %w", err)
	}

	tid, _ := domain.ParseTargetID(targetIDRaw)
	inc.TargetID = tid
	inc.IncidentType = domain.IncidentType(incType)
	inc.Channel = domain.ChannelType(channel)
	inc.DeliveryPath = domain.DeliveryPath(deliveryPath)
	inc.Status = domain.IncidentStatus(status)
	inc.EventTimestamp, _ = time.Parse(time.RFC3339Nano, eventTs)
	inc.ReceivedAt, _ = time.Parse(time.RFC3339Nano, recTs)
	inc.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	inc.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)

	return &inc, nil
}

func (r *sqliteTelemetryRepo) ListIncidents(ctx context.Context, filter repository.IncidentFilter) ([]*domain.Incident, error) {
	var conditions []string
	var args []interface{}

	if filter.IncidentType != "" {
		conditions = append(conditions, "incident_type = ?")
		args = append(args, filter.IncidentType)
	}
	if filter.Status != "" {
		conditions = append(conditions, "status = ?")
		args = append(args, filter.Status)
	}
	if filter.RegionCode != "" {
		conditions = append(conditions, "region_code = ?")
		args = append(args, filter.RegionCode)
	}
	if filter.Since != nil {
		conditions = append(conditions, "received_at >= ?")
		args = append(args, filter.Since.Format(time.RFC3339Nano))
	}

	whereClause := ""
	if len(conditions) > 0 {
		whereClause = "WHERE " + strings.Join(conditions, " AND ")
	}

	limit := filter.Limit
	if limit <= 0 {
		limit = 50
	}
	offset := filter.Offset

	query := fmt.Sprintf(`
		SELECT id, packet_id, packet_hash, target_id_raw, incident_type, channel, delivery_path,
		       latitude, longitude, altitude, gps_accuracy, hop_count, ttl, sender_device_id,
		       status, event_timestamp, received_at, created_at, updated_at
		FROM incidents %s
		ORDER BY received_at DESC
		LIMIT ? OFFSET ?
	`, whereClause)

	args = append(args, limit, offset)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("failed to query incidents: %w", err)
	}
	defer rows.Close()

	var results []*domain.Incident
	for rows.Next() {
		var inc domain.Incident
		var targetIDRaw, incType, channel, deliveryPath, status string
		var eventTs, recTs, crTs, upTs string

		if err := rows.Scan(
			&inc.ID, &inc.PacketID, &inc.PacketHash, &targetIDRaw, &incType, &channel, &deliveryPath,
			&inc.Latitude, &inc.Longitude, &inc.Altitude, &inc.GPSAccuracy, &inc.HopCount, &inc.TTL,
			&inc.SenderDeviceID, &status, &eventTs, &recTs, &crTs, &upTs,
		); err != nil {
			return nil, fmt.Errorf("failed to scan incident row: %w", err)
		}

		tid, _ := domain.ParseTargetID(targetIDRaw)
		inc.TargetID = tid
		inc.IncidentType = domain.IncidentType(incType)
		inc.Channel = domain.ChannelType(channel)
		inc.DeliveryPath = domain.DeliveryPath(deliveryPath)
		inc.Status = domain.IncidentStatus(status)
		inc.EventTimestamp, _ = time.Parse(time.RFC3339Nano, eventTs)
		inc.ReceivedAt, _ = time.Parse(time.RFC3339Nano, recTs)
		inc.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		inc.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)

		results = append(results, &inc)
	}

	return results, nil
}

func (r *sqliteTelemetryRepo) UpdateIncidentStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus) error {
	now := time.Now().UTC().Format(time.RFC3339Nano)
	query := `UPDATE incidents SET status = ?, updated_at = ? WHERE id = ?`
	res, err := r.db.ExecContext(ctx, query, string(status), now, incidentID)
	if err != nil {
		return fmt.Errorf("failed to update incident status: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteTelemetryRepo) Ping(ctx context.Context) error {
	return r.db.PingContext(ctx)
}

func (r *sqliteTelemetryRepo) Close() error {
	return r.db.Close()
}
