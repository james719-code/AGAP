package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type sqliteAnalyticsRepo struct{ db *sql.DB }

func NewAnalyticsRepo(db *sql.DB) *sqliteAnalyticsRepo { return &sqliteAnalyticsRepo{db: db} }

const realPacketPredicate = `(mp.incident_id IS NULL OR EXISTS (
	SELECT 1 FROM incidents i WHERE i.id = mp.incident_id AND COALESCE(i.is_demo, 0) = 0
))`

func (r *sqliteAnalyticsRepo) GetTelemetryInsights(ctx context.Context) (*domain.TelemetryInsights, error) {
	result := &domain.TelemetryInsights{}
	var meshDeliveryEvidence int64

	queries := []struct {
		query string
		dest  *int64
	}{
		{`SELECT COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0`, &result.TotalIncidents},
		{`SELECT COUNT(*) FROM mesh_packets mp WHERE ` + realPacketPredicate, &result.TotalPackets},
		{`SELECT COUNT(*) FROM mesh_delivery_events e JOIN mesh_packets mp ON mp.id = e.packet_id WHERE ` + realPacketPredicate, &result.TotalDeliveryEvents},
		{`SELECT COUNT(*) FROM mesh_packets mp JOIN incidents i ON i.id = mp.incident_id WHERE COALESCE(i.is_demo, 0) = 0 AND i.channel = 'BLE_MESH'`, &result.MeshPackets},
		{`SELECT COUNT(DISTINCT mp.id) FROM mesh_packets mp JOIN incidents i ON i.id = mp.incident_id JOIN mesh_delivery_events e ON e.packet_id = mp.id WHERE COALESCE(i.is_demo, 0) = 0 AND i.channel = 'BLE_MESH' AND e.event_type IN ('UPLINKED','TARGET_REACHED')`, &result.DeliveredMeshPackets},
		{`SELECT COUNT(*) FROM mesh_delivery_events e JOIN mesh_packets mp ON mp.id = e.packet_id JOIN incidents i ON i.id = mp.incident_id WHERE COALESCE(i.is_demo, 0) = 0 AND i.channel = 'BLE_MESH'`, &meshDeliveryEvidence},
		{`SELECT COUNT(DISTINCT mp.id) FROM mesh_packets mp JOIN mesh_delivery_events e ON e.packet_id = mp.id WHERE ` + realPacketPredicate + ` AND e.event_type = 'RELAYED'`, &result.RelayedPackets},
		{`SELECT COUNT(DISTINCT NULLIF(TRIM(e.device_identifier), '')) FROM mesh_delivery_events e JOIN mesh_packets mp ON mp.id = e.packet_id WHERE ` + realPacketPredicate + ` AND e.event_type = 'RELAYED'`, &result.UniqueRelayDevices},
		{`SELECT COUNT(*) FROM mesh_delivery_events e JOIN mesh_packets mp ON mp.id = e.packet_id WHERE ` + realPacketPredicate + ` AND e.event_type = 'DUPLICATE_DROPPED'`, &result.DuplicateDrops},
		{`SELECT COUNT(*) FROM rescue_units`, &result.TotalResponders},
		{`SELECT COUNT(*) FROM rescue_units WHERE status <> 'OFFLINE' AND (last_location_at IS NULL OR TRIM(last_location_at) = '' OR datetime(last_location_at) >= datetime('now', '-15 minutes'))`, &result.ActiveResponders},
	}
	for _, item := range queries {
		if err := r.db.QueryRowContext(ctx, item.query).Scan(item.dest); err != nil {
			return nil, fmt.Errorf("query telemetry insights: %w", err)
		}
	}

	if result.MeshPackets > 0 && meshDeliveryEvidence > 0 {
		rate := float64(result.DeliveredMeshPackets) * 100 / float64(result.MeshPackets)
		result.MeshDeliveryRate = &rate
		var average float64
		err := r.db.QueryRowContext(ctx, `
			SELECT COALESCE(AVG(relay_count), 0) FROM (
				SELECT mp.id, COUNT(CASE WHEN e.event_type = 'RELAYED' THEN 1 END) relay_count
				FROM mesh_packets mp
				JOIN incidents i ON i.id = mp.incident_id
				LEFT JOIN mesh_delivery_events e ON e.packet_id = mp.id
				WHERE COALESCE(i.is_demo, 0) = 0 AND i.channel = 'BLE_MESH'
				GROUP BY mp.id
			)
		`).Scan(&average)
		if err != nil {
			return nil, fmt.Errorf("query average relay hops: %w", err)
		}
		result.AverageRelayHops = &average
	}

	var err error
	if result.IncidentTypeDistribution, err = r.breakdown(ctx, `SELECT incident_type, COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0 GROUP BY incident_type ORDER BY COUNT(*) DESC, incident_type`); err != nil {
		return nil, err
	}
	if result.ChannelDistribution, err = r.breakdown(ctx, `SELECT channel, COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0 GROUP BY channel ORDER BY COUNT(*) DESC, channel`); err != nil {
		return nil, err
	}
	if result.DeliveryPathDistribution, err = r.breakdown(ctx, `SELECT delivery_path, COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0 GROUP BY delivery_path ORDER BY COUNT(*) DESC, delivery_path`); err != nil {
		return nil, err
	}
	return result, nil
}

func (r *sqliteAnalyticsRepo) breakdown(ctx context.Context, query string) ([]domain.AnalyticsBreakdown, error) {
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("query analytics breakdown: %w", err)
	}
	defer rows.Close()
	items := make([]domain.AnalyticsBreakdown, 0)
	for rows.Next() {
		var item domain.AnalyticsBreakdown
		if err := rows.Scan(&item.Label, &item.Count); err != nil {
			return nil, fmt.Errorf("scan analytics breakdown: %w", err)
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *sqliteAnalyticsRepo) GetOperationalReport(ctx context.Context) (*domain.OperationalReport, error) {
	report := &domain.OperationalReport{}
	counts := []struct {
		query string
		dest  *int64
	}{
		{`SELECT COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0`, &report.TotalIncidents},
		{`SELECT COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0 AND status NOT IN ('RESOLVED','CANCELLED','FALSE_ALARM')`, &report.ActiveIncidents},
		{`SELECT COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0 AND status = 'RESOLVED'`, &report.ResolvedIncidents},
		{`SELECT COUNT(*) FROM incidents WHERE COALESCE(is_demo, 0) = 0 AND (status = 'FALSE_ALARM' OR false_positive = 1)`, &report.FalseAlarms},
		{`SELECT COUNT(*) FROM rescue_units`, &report.TotalResponders},
		{`SELECT COUNT(*) FROM rescue_units WHERE status <> 'OFFLINE' AND datetime(last_location_at) >= datetime('now', '-15 minutes')`, &report.ActiveResponders},
	}
	for _, item := range counts {
		if err := r.db.QueryRowContext(ctx, item.query).Scan(item.dest); err != nil {
			return nil, fmt.Errorf("query operational report: %w", err)
		}
	}
	if report.TotalIncidents > 0 {
		rate := float64(report.FalseAlarms) * 100 / float64(report.TotalIncidents)
		report.FalseAlarmRate = &rate
	}

	averages := []struct {
		query string
		dest  **float64
	}{
		{`SELECT AVG((julianday(ack_at)-julianday(received_at))*1440.0) FROM (SELECT i.id, i.received_at, MIN(h.created_at) ack_at FROM incidents i JOIN incident_status_history h ON h.incident_id=i.id AND h.status='ACKNOWLEDGED' WHERE COALESCE(i.is_demo,0)=0 GROUP BY i.id) WHERE ack_at >= received_at`, &report.AverageAckMinutes},
		{`SELECT AVG((julianday(dispatched_at)-julianday(received_at))*1440.0) FROM (SELECT i.id, i.received_at, MIN(d.dispatched_at) dispatched_at FROM incidents i JOIN incident_dispatches d ON d.incident_id=i.id WHERE COALESCE(i.is_demo,0)=0 GROUP BY i.id) WHERE dispatched_at >= received_at`, &report.AverageDispatchMinutes},
		{`SELECT AVG((julianday(d.arrived_at)-julianday(d.dispatched_at))*1440.0) FROM incident_dispatches d JOIN incidents i ON i.id=d.incident_id WHERE COALESCE(i.is_demo,0)=0 AND d.arrived_at IS NOT NULL AND d.arrived_at >= d.dispatched_at`, &report.AverageArrivalMinutes},
		{`SELECT AVG((julianday(resolved_at)-julianday(received_at))*1440.0) FROM incidents WHERE COALESCE(is_demo,0)=0 AND resolved_at IS NOT NULL AND resolved_at >= received_at`, &report.AverageResponseMinutes},
	}
	for _, item := range averages {
		var value sql.NullFloat64
		if err := r.db.QueryRowContext(ctx, item.query).Scan(&value); err != nil {
			return nil, fmt.Errorf("query operational average: %w", err)
		}
		if value.Valid {
			v := value.Float64
			*item.dest = &v
		}
	}

	breakdowns := []struct {
		query string
		dest  *[]domain.AnalyticsBreakdown
	}{
		{`SELECT status, COUNT(*) FROM incidents WHERE COALESCE(is_demo,0)=0 GROUP BY status ORDER BY COUNT(*) DESC, status`, &report.StatusDistribution},
		{`SELECT incident_type, COUNT(*) FROM incidents WHERE COALESCE(is_demo,0)=0 GROUP BY incident_type ORDER BY COUNT(*) DESC, incident_type`, &report.IncidentTypeDistribution},
		{`SELECT municipality_code || ' / ' || barangay_code, COUNT(*) FROM incidents WHERE COALESCE(is_demo,0)=0 GROUP BY municipality_code, barangay_code ORDER BY COUNT(*) DESC`, &report.BarangayDistribution},
		{`SELECT a.name, COUNT(*) FROM incident_dispatches d JOIN incidents i ON i.id=d.incident_id JOIN rescue_units ru ON ru.id=d.unit_id JOIN agencies a ON a.id=ru.agency_id WHERE COALESCE(i.is_demo,0)=0 GROUP BY a.id, a.name ORDER BY COUNT(*) DESC`, &report.AgencyDispatches},
		{`SELECT COALESCE(NULLIF(ru.responder_name,''), ru.unit_code), COUNT(*) FROM incident_dispatches d JOIN incidents i ON i.id=d.incident_id JOIN rescue_units ru ON ru.id=d.unit_id WHERE COALESCE(i.is_demo,0)=0 GROUP BY ru.id ORDER BY COUNT(*) DESC`, &report.ResponderAssignments},
		{`SELECT ru.unit_code, COUNT(*) FROM incident_dispatches d JOIN incidents i ON i.id=d.incident_id JOIN rescue_units ru ON ru.id=d.unit_id WHERE COALESCE(i.is_demo,0)=0 GROUP BY ru.id, ru.unit_code ORDER BY COUNT(*) DESC`, &report.UnitDeployments},
	}
	for _, item := range breakdowns {
		rows, err := r.breakdown(ctx, item.query)
		if err != nil {
			return nil, err
		}
		*item.dest = rows
	}
	return report, nil
}
