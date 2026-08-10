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
	incidentUUID := packet.IncidentUUID
	if incidentUUID == "" {
		incidentUUID = packet.PacketUUID
	}

	query := `
		INSERT INTO incidents (
			incident_uuid, packet_hash, citizen_id, target_id_raw, region_code, province_code,
			municipality_code, barangay_code, zone_code, incident_type, channel,
			delivery_path, latitude, longitude, altitude, gps_accuracy,
			hop_count, ttl, sender_device_id, status, event_timestamp, received_at,
			created_at, updated_at, emergency_type_id
		) VALUES (?, ?,
			(SELECT u.id
			 FROM users u
			 LEFT JOIN user_devices ud ON ud.user_id = u.id
			 WHERE ? <> '' AND u.role = 'CITIZEN'
			   AND (u.device_id = ? OR ud.device_hardware_id = ?)
			 ORDER BY CASE WHEN u.device_id = ? THEN 0 ELSE 1 END
			 LIMIT 1),
			?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?, ?, ?, ?,
			(SELECT id FROM emergency_types WHERE code = ?)
		)
	`

	res, err := r.db.ExecContext(
		ctx, query,
		incidentUUID, packet.PacketHash,
		packet.SenderDeviceID, packet.SenderDeviceID, packet.SenderDeviceID, packet.SenderDeviceID,
		packet.TargetID.Raw, packet.TargetID.Region, packet.TargetID.Province, packet.TargetID.Municipality,
		packet.TargetID.Barangay, packet.TargetID.Zone, string(packet.IncidentType),
		string(packet.Channel), string(packet.DeliveryPath), packet.Latitude,
		packet.Longitude, packet.Altitude, packet.GPSAccuracy, packet.HopCount,
		packet.TTL, packet.SenderDeviceID, eventTime.Format(time.RFC3339Nano),
		receivedAt.Format(time.RFC3339Nano), now.Format(time.RFC3339Nano), now.Format(time.RFC3339Nano),
		string(packet.IncidentType),
	)

	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE constraint failed") {
			if inc, fetchErr := r.GetIncidentByIncidentUUID(ctx, incidentUUID); fetchErr == nil {
				if repairErr := r.ensureMeshPacketIdentity(ctx, packet, inc.ID, receivedAt); repairErr != nil {
					return nil, true, repairErr
				}
				return inc, true, nil
			}
			if inc, fetchErr := r.GetIncidentByPacketUUID(ctx, packet.PacketUUID); fetchErr == nil {
				return inc, true, nil
			}
			if inc, fetchErr := r.GetIncidentByPacketHash(ctx, packet.PacketHash); fetchErr == nil {
				if repairErr := r.ensureMeshPacketIdentity(ctx, packet, inc.ID, receivedAt); repairErr != nil {
					return nil, true, repairErr
				}
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

	if err := r.ensureMeshPacketIdentity(ctx, packet, id, receivedAt); err != nil {
		return nil, false, err
	}

	_ = r.AppendIncidentStatusHistory(ctx, &domain.IncidentStatusHistory{
		IncidentID: id,
		Status:     domain.StatusReceived,
		Notes:      "Incident received by backend",
		CreatedAt:  now,
	})

	// Re-read the row so server-computed ownership/foreign keys are reflected in
	// the object returned to notification, WebSocket, and sync callers.
	inc, err := r.GetIncidentByID(ctx, id)
	if err != nil {
		return nil, false, fmt.Errorf("failed to reload saved incident: %w", err)
	}
	return inc, false, nil

}

func (r *sqliteTelemetryRepo) ensureMeshPacketIdentity(ctx context.Context, packet *domain.TelemetryPacket, incidentID int64, receivedAt time.Time) error {
	if packet == nil || packet.PacketUUID == "" {
		return nil
	}
	now := time.Now().UTC()
	if receivedAt.IsZero() {
		receivedAt = now
	}
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO mesh_packets (
			packet_uuid, packet_type, incident_id, origin_device_identifier,
			payload_hash, created_at, server_received_at
		) VALUES (?, 'SOS', ?, ?, ?, ?, ?)
		ON CONFLICT(packet_uuid) DO UPDATE SET
			incident_id = COALESCE(mesh_packets.incident_id, excluded.incident_id),
			payload_hash = COALESCE(NULLIF(mesh_packets.payload_hash, ''), excluded.payload_hash),
			server_received_at = COALESCE(mesh_packets.server_received_at, excluded.server_received_at)
	`, packet.PacketUUID, incidentID, packet.SenderDeviceID, packet.PacketHash, now.Format(time.RFC3339Nano), receivedAt.UTC().Format(time.RFC3339Nano))
	if err != nil {
		return fmt.Errorf("failed to record mesh packet identity: %w", err)
	}
	return nil
}

func (r *sqliteTelemetryRepo) RecordMeshDeliveryEvent(ctx context.Context, event *domain.MeshDeliveryEvent) error {
	if event == nil {
		return nil
	}
	if event.PacketUUID == "" {
		return fmt.Errorf("packet_uuid is required")
	}
	if event.PacketType == "" {
		event.PacketType = domain.MeshPacketTypeSOS
	}
	if event.EventType == "" {
		event.EventType = domain.MeshEventUplinked
	}
	if event.RecordedAt.IsZero() {
		event.RecordedAt = time.Now().UTC()
	}

	now := time.Now().UTC().Format(time.RFC3339Nano)
	recordedAt := event.RecordedAt.UTC().Format(time.RFC3339Nano)

	_, err := r.db.ExecContext(ctx, `
		INSERT INTO mesh_packets (
			packet_uuid, packet_type, incident_id, origin_device_identifier,
			payload_hash, created_at, server_received_at
		) VALUES (?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(packet_uuid) DO UPDATE SET
			incident_id = COALESCE(mesh_packets.incident_id, excluded.incident_id),
			payload_hash = COALESCE(NULLIF(mesh_packets.payload_hash, ''), excluded.payload_hash),
			server_received_at = COALESCE(mesh_packets.server_received_at, excluded.server_received_at)
	`, event.PacketUUID, string(event.PacketType), event.IncidentID, event.OriginDeviceID, event.PayloadHash, now, now)
	if err != nil {
		return fmt.Errorf("failed to upsert mesh packet: %w", err)
	}

	var meshPacketID int64
	if err := r.db.QueryRowContext(ctx, `SELECT id FROM mesh_packets WHERE packet_uuid = ?`, event.PacketUUID).Scan(&meshPacketID); err != nil {
		return fmt.Errorf("failed to fetch mesh packet id: %w", err)
	}

	var syncedAt interface{}
	if event.SyncedAt != nil {
		syncedAt = event.SyncedAt.UTC().Format(time.RFC3339Nano)
	}

	_, err = r.db.ExecContext(ctx, `
		INSERT INTO mesh_delivery_events (
			packet_id, device_identifier, event_type, latitude, longitude, recorded_at, synced_at
		) VALUES (?, ?, ?, ?, ?, ?, ?)
	`, meshPacketID, event.DeviceIdentifier, string(event.EventType), event.Latitude, event.Longitude, recordedAt, syncedAt)
	if err != nil {
		return fmt.Errorf("failed to insert mesh delivery event: %w", err)
	}
	return nil
}

func (r *sqliteTelemetryRepo) AppendIncidentStatusHistory(ctx context.Context, event *domain.IncidentStatusHistory) error {
	if event.CreatedAt.IsZero() {
		event.CreatedAt = time.Now().UTC()
	}

	query := `
		INSERT INTO incident_status_history (incident_id, status, changed_by, latitude, longitude, notes, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query,
		event.IncidentID, string(event.Status), event.ChangedBy, event.Latitude, event.Longitude, event.Notes, event.CreatedAt.Format(time.RFC3339Nano),
	)
	if err != nil {
		return fmt.Errorf("failed to append incident status history: %w", err)
	}
	if id, err := res.LastInsertId(); err == nil {
		event.ID = id
	}
	return nil
}

func (r *sqliteTelemetryRepo) ListIncidentStatusHistory(ctx context.Context, incidentID int64) ([]*domain.IncidentStatusHistory, error) {
	query := `
		SELECT id, incident_id, status, changed_by, latitude, longitude, COALESCE(notes, ''), created_at
		FROM incident_status_history
		WHERE incident_id = ?
		ORDER BY created_at ASC, id ASC
	`
	rows, err := r.db.QueryContext(ctx, query, incidentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	history := make([]*domain.IncidentStatusHistory, 0)
	for rows.Next() {
		var event domain.IncidentStatusHistory
		var status, createdAt string
		var changedBy sql.NullInt64
		var latitude, longitude sql.NullFloat64

		if err := rows.Scan(&event.ID, &event.IncidentID, &status, &changedBy, &latitude, &longitude, &event.Notes, &createdAt); err != nil {
			return nil, err
		}
		event.Status = domain.IncidentStatus(status)
		if changedBy.Valid {
			event.ChangedBy = &changedBy.Int64
		}
		if latitude.Valid {
			event.Latitude = &latitude.Float64
		}
		if longitude.Valid {
			event.Longitude = &longitude.Float64
		}
		event.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdAt)
		history = append(history, &event)
	}
	return history, nil
}

// ListIncidentStatusHistoryBulk fetches status history for many incidents in
// a single query, keyed by incident ID. Used by reporting views that would
// otherwise call ListIncidentStatusHistory once per incident (N+1).
func (r *sqliteTelemetryRepo) ListIncidentStatusHistoryBulk(ctx context.Context, incidentIDs []int64) (map[int64][]*domain.IncidentStatusHistory, error) {
	result := make(map[int64][]*domain.IncidentStatusHistory, len(incidentIDs))
	if len(incidentIDs) == 0 {
		return result, nil
	}

	placeholders := make([]string, len(incidentIDs))
	args := make([]interface{}, len(incidentIDs))
	for i, id := range incidentIDs {
		placeholders[i] = "?"
		args[i] = id
	}

	query := fmt.Sprintf(`
		SELECT id, incident_id, status, changed_by, latitude, longitude, COALESCE(notes, ''), created_at
		FROM incident_status_history
		WHERE incident_id IN (%s)
		ORDER BY incident_id ASC, created_at ASC, id ASC
	`, strings.Join(placeholders, ","))

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var event domain.IncidentStatusHistory
		var status, createdAt string
		var changedBy sql.NullInt64
		var latitude, longitude sql.NullFloat64

		if err := rows.Scan(&event.ID, &event.IncidentID, &status, &changedBy, &latitude, &longitude, &event.Notes, &createdAt); err != nil {
			return nil, err
		}
		event.Status = domain.IncidentStatus(status)
		if changedBy.Valid {
			event.ChangedBy = &changedBy.Int64
		}
		if latitude.Valid {
			event.Latitude = &latitude.Float64
		}
		if longitude.Valid {
			event.Longitude = &longitude.Float64
		}
		event.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdAt)
		result[event.IncidentID] = append(result[event.IncidentID], &event)
	}
	return result, nil
}

func (r *sqliteTelemetryRepo) GetIncidentByID(ctx context.Context, id int64) (*domain.Incident, error) {
	query := `
		SELECT i.id, i.incident_uuid, COALESCE(mp.packet_uuid, ''), i.packet_hash, i.citizen_id, i.responder_id, i.dispatched_unit_id,
		       i.emergency_type_id, i.address_id,
		       i.target_id_raw, i.incident_type, i.channel, i.delivery_path,
		       i.latitude, i.longitude, i.altitude, i.gps_accuracy, i.hop_count, i.ttl, i.sender_device_id,
		       i.false_positive, i.status, i.description, i.dispatcher_notes, i.event_timestamp, i.received_at,
		       i.dispatched_at, i.resolved_at, i.created_at, i.updated_at
		FROM incidents i
		LEFT JOIN mesh_packets mp ON mp.incident_id = i.id
		WHERE i.id = ?
	`
	row := r.db.QueryRowContext(ctx, query, id)

	var inc domain.Incident
	var targetIDRaw, incType, channel, deliveryPath, status string
	var eventTs, recTs, crTs, upTs string
	var dispTs, resTs sql.NullString
	var fpInt int

	err := row.Scan(
		&inc.ID, &inc.IncidentUUID, &inc.PacketUUID, &inc.PacketHash, &inc.CitizenID, &inc.ResponderID, &inc.DispatchedUnitID, &inc.EmergencyTypeID, &inc.AddressID,
		&targetIDRaw, &incType, &channel, &deliveryPath,
		&inc.Latitude, &inc.Longitude, &inc.Altitude, &inc.GPSAccuracy, &inc.HopCount, &inc.TTL,
		&inc.SenderDeviceID, &fpInt, &status, &inc.Description, &inc.DispatcherNotes, &eventTs, &recTs,
		&dispTs, &resTs, &crTs, &upTs,
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
	inc.FalsePositive = fpInt == 1
	inc.Status = domain.IncidentStatus(status)
	inc.EventTimestamp, _ = time.Parse(time.RFC3339Nano, eventTs)
	inc.ReceivedAt, _ = time.Parse(time.RFC3339Nano, recTs)
	if dispTs.Valid && dispTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, dispTs.String)
		inc.DispatchedAt = &t
	}
	if resTs.Valid && resTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, resTs.String)
		inc.ResolvedAt = &t
	}
	inc.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	inc.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)

	return &inc, nil
}

func (r *sqliteTelemetryRepo) GetIncidentByIncidentUUID(ctx context.Context, incidentUUID string) (*domain.Incident, error) {
	query := `
		SELECT i.id, i.incident_uuid, COALESCE(mp.packet_uuid, ''), i.packet_hash, i.citizen_id, i.responder_id, i.dispatched_unit_id,
		       i.emergency_type_id, i.address_id,
		       i.target_id_raw, i.incident_type, i.channel, i.delivery_path,
		       i.latitude, i.longitude, i.altitude, i.gps_accuracy, i.hop_count, i.ttl, i.sender_device_id,
		       i.false_positive, i.status, i.description, i.dispatcher_notes, i.event_timestamp, i.received_at,
		       i.dispatched_at, i.resolved_at, i.created_at, i.updated_at
		FROM incidents i
		LEFT JOIN mesh_packets mp ON mp.incident_id = i.id
		WHERE i.incident_uuid = ?
	`
	row := r.db.QueryRowContext(ctx, query, incidentUUID)
	return scanIncident(row)
}

type incidentScanner interface {
	Scan(dest ...interface{}) error
}

func scanIncident(scanner incidentScanner) (*domain.Incident, error) {
	var inc domain.Incident
	var targetIDRaw, incType, channel, deliveryPath, status string
	var eventTs, recTs, crTs, upTs string
	var dispTs, resTs sql.NullString
	var fpInt int

	err := scanner.Scan(
		&inc.ID, &inc.IncidentUUID, &inc.PacketUUID, &inc.PacketHash, &inc.CitizenID, &inc.ResponderID, &inc.DispatchedUnitID, &inc.EmergencyTypeID, &inc.AddressID,
		&targetIDRaw, &incType, &channel, &deliveryPath,
		&inc.Latitude, &inc.Longitude, &inc.Altitude, &inc.GPSAccuracy, &inc.HopCount, &inc.TTL,
		&inc.SenderDeviceID, &fpInt, &status, &inc.Description, &inc.DispatcherNotes, &eventTs, &recTs,
		&dispTs, &resTs, &crTs, &upTs,
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
	inc.FalsePositive = fpInt == 1
	inc.Status = domain.IncidentStatus(status)
	inc.EventTimestamp, _ = time.Parse(time.RFC3339Nano, eventTs)
	inc.ReceivedAt, _ = time.Parse(time.RFC3339Nano, recTs)
	if dispTs.Valid && dispTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, dispTs.String)
		inc.DispatchedAt = &t
	}
	if resTs.Valid && resTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, resTs.String)
		inc.ResolvedAt = &t
	}
	inc.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	inc.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)

	return &inc, nil
}

func (r *sqliteTelemetryRepo) GetIncidentByPacketUUID(ctx context.Context, packetUUID string) (*domain.Incident, error) {
	query := `
		SELECT i.id, i.incident_uuid, COALESCE(mp.packet_uuid, ''), i.packet_hash, i.citizen_id, i.responder_id, i.dispatched_unit_id,
		       i.emergency_type_id, i.address_id,
		       i.target_id_raw, i.incident_type, i.channel, i.delivery_path,
		       i.latitude, i.longitude, i.altitude, i.gps_accuracy, i.hop_count, i.ttl, i.sender_device_id,
		       i.false_positive, i.status, i.description, i.dispatcher_notes, i.event_timestamp, i.received_at,
		       i.dispatched_at, i.resolved_at, i.created_at, i.updated_at
		FROM mesh_packets mp
		JOIN incidents i ON i.id = mp.incident_id
		WHERE mp.packet_uuid = ?
	`
	row := r.db.QueryRowContext(ctx, query, packetUUID)
	return scanIncident(row)
}

func (r *sqliteTelemetryRepo) GetIncidentByPacketHash(ctx context.Context, packetHash string) (*domain.Incident, error) {
	query := `
		SELECT i.id, i.incident_uuid, COALESCE(mp.packet_uuid, ''), i.packet_hash, i.citizen_id, i.responder_id, i.dispatched_unit_id,
		       i.emergency_type_id, i.address_id,
		       i.target_id_raw, i.incident_type, i.channel, i.delivery_path,
		       i.latitude, i.longitude, i.altitude, i.gps_accuracy, i.hop_count, i.ttl, i.sender_device_id,
		       i.false_positive, i.status, i.description, i.dispatcher_notes, i.event_timestamp, i.received_at,
		       i.dispatched_at, i.resolved_at, i.created_at, i.updated_at
		FROM incidents i
		LEFT JOIN mesh_packets mp ON mp.incident_id = i.id
		WHERE i.packet_hash = ?
	`
	row := r.db.QueryRowContext(ctx, query, packetHash)
	return scanIncident(row)
}

func (r *sqliteTelemetryRepo) ListIncidents(ctx context.Context, filter repository.IncidentFilter) ([]*domain.Incident, int, error) {
	var conditions []string
	var args []interface{}

	if filter.IncidentType != "" {
		conditions = append(conditions, "i.incident_type = ?")
		args = append(args, filter.IncidentType)
	}
	if filter.Status != "" {
		conditions = append(conditions, "i.status = ?")
		args = append(args, filter.Status)
	}
	if filter.RegionCode != "" {
		conditions = append(conditions, "i.region_code = ?")
		args = append(args, filter.RegionCode)
	}
	if filter.CitizenID != nil {
		conditions = append(conditions, `(i.citizen_id = ? OR EXISTS (
			SELECT 1 FROM incident_reports ir
			WHERE ir.incident_id = i.id AND ir.reporter_user_id = ?
		))`)
		args = append(args, *filter.CitizenID, *filter.CitizenID)
	}
	if filter.Since != nil {
		conditions = append(conditions, "i.received_at >= ?")
		args = append(args, filter.Since.Format(time.RFC3339Nano))
	}

	whereClause := ""
	if len(conditions) > 0 {
		whereClause = "WHERE " + strings.Join(conditions, " AND ")
	}

	// First query total count
	countQuery := fmt.Sprintf(`SELECT COUNT(*) FROM incidents i %s`, whereClause)
	var totalCount int
	if err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&totalCount); err != nil {
		return nil, 0, fmt.Errorf("failed to count incidents: %w", err)
	}

	limit := filter.Limit
	if limit <= 0 {
		limit = 50
	}
	offset := filter.Offset

	query := fmt.Sprintf(`
		SELECT i.id, i.incident_uuid, COALESCE(mp.packet_uuid, ''), i.packet_hash, i.citizen_id, i.responder_id, i.dispatched_unit_id,
		       i.emergency_type_id, i.address_id,
		       i.target_id_raw, i.incident_type, i.channel, i.delivery_path,
		       i.latitude, i.longitude, i.altitude, i.gps_accuracy, i.hop_count, i.ttl, i.sender_device_id,
		       i.false_positive, i.status, i.description, i.dispatcher_notes, i.event_timestamp, i.received_at,
		       i.dispatched_at, i.resolved_at, i.created_at, i.updated_at
		FROM incidents i
		LEFT JOIN mesh_packets mp ON mp.incident_id = i.id
		%s
		ORDER BY i.received_at DESC
		LIMIT ? OFFSET ?
	`, whereClause)

	queryArgs := append(args, limit, offset)

	rows, err := r.db.QueryContext(ctx, query, queryArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to query incidents: %w", err)
	}
	defer rows.Close()

	results := make([]*domain.Incident, 0)
	for rows.Next() {
		var inc domain.Incident
		var targetIDRaw, incType, channel, deliveryPath, status string
		var eventTs, recTs, crTs, upTs string
		var dispTs, resTs sql.NullString
		var fpInt int

		if err := rows.Scan(
			&inc.ID, &inc.IncidentUUID, &inc.PacketUUID, &inc.PacketHash, &inc.CitizenID, &inc.ResponderID, &inc.DispatchedUnitID, &inc.EmergencyTypeID, &inc.AddressID,
			&targetIDRaw, &incType, &channel, &deliveryPath,
			&inc.Latitude, &inc.Longitude, &inc.Altitude, &inc.GPSAccuracy, &inc.HopCount, &inc.TTL,
			&inc.SenderDeviceID, &fpInt, &status, &inc.Description, &inc.DispatcherNotes, &eventTs, &recTs,
			&dispTs, &resTs, &crTs, &upTs,
		); err != nil {
			return nil, 0, fmt.Errorf("failed to scan incident row: %w", err)
		}

		tid, _ := domain.ParseTargetID(targetIDRaw)
		inc.TargetID = tid
		inc.IncidentType = domain.IncidentType(incType)
		inc.Channel = domain.ChannelType(channel)
		inc.DeliveryPath = domain.DeliveryPath(deliveryPath)
		inc.FalsePositive = fpInt == 1
		inc.Status = domain.IncidentStatus(status)
		inc.EventTimestamp, _ = time.Parse(time.RFC3339Nano, eventTs)
		inc.ReceivedAt, _ = time.Parse(time.RFC3339Nano, recTs)
		if dispTs.Valid && dispTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, dispTs.String)
			inc.DispatchedAt = &t
		}
		if resTs.Valid && resTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, resTs.String)
			inc.ResolvedAt = &t
		}
		inc.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		inc.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)

		results = append(results, &inc)
	}

	return results, totalCount, nil
}

func (r *sqliteTelemetryRepo) CanCitizenAccessIncident(ctx context.Context, incidentID, userID int64) (bool, error) {
	var allowed int
	err := r.db.QueryRowContext(ctx, `
		SELECT CASE WHEN EXISTS (
			SELECT 1
			FROM incidents i
			WHERE i.id = ? AND (
				i.citizen_id = ? OR EXISTS (
					SELECT 1 FROM incident_reports ir
					WHERE ir.incident_id = i.id AND ir.reporter_user_id = ?
				)
			)
		) THEN 1 ELSE 0 END
	`, incidentID, userID, userID).Scan(&allowed)
	if err != nil {
		return false, err
	}
	return allowed == 1, nil
}

func (r *sqliteTelemetryRepo) UpdateIncidentStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus) error {
	now := time.Now().UTC().Format(time.RFC3339Nano)
	var query string
	var args []interface{}

	switch status {
	case domain.StatusDispatched:
		query = `UPDATE incidents SET status = ?, dispatched_at = COALESCE(dispatched_at, ?), updated_at = ? WHERE id = ?`
		args = []interface{}{string(status), now, now, incidentID}
	case domain.StatusEnRoute:
		query = `UPDATE incidents SET status = ?, dispatched_at = COALESCE(dispatched_at, ?), updated_at = ? WHERE id = ?`
		args = []interface{}{string(status), now, now, incidentID}
	case domain.StatusOnScene:
		query = `UPDATE incidents SET status = ?, updated_at = ? WHERE id = ?`
		args = []interface{}{string(status), now, incidentID}
	case domain.StatusResolved:
		query = `UPDATE incidents SET status = ?, resolved_at = COALESCE(resolved_at, ?), updated_at = ? WHERE id = ?`
		args = []interface{}{string(status), now, now, incidentID}
	default:
		query = `UPDATE incidents SET status = ?, updated_at = ? WHERE id = ?`
		args = []interface{}{string(status), now, incidentID}
	}

	res, err := r.db.ExecContext(ctx, query, args...)
	if err != nil {
		return fmt.Errorf("failed to update incident status: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteTelemetryRepo) UpdateIncidentLocation(ctx context.Context, incidentID int64, lat, lng float64) error {
	now := time.Now().UTC().Format(time.RFC3339Nano)
	query := `UPDATE incidents SET latitude = ?, longitude = ?, updated_at = ? WHERE id = ? AND status NOT IN ('RESOLVED', 'CANCELLED', 'FALSE_ALARM')`
	res, err := r.db.ExecContext(ctx, query, lat, lng, now, incidentID)
	if err != nil {
		return fmt.Errorf("failed to update incident location: %w", err)
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