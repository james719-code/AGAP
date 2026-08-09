package sqlite

import (
	"context"
	"path/filepath"
	"testing"
)

func TestAnalyticsRepoUsesRealDeliveryEventsAndExcludesDemoIncidents(t *testing.T) {
	db, err := Open(filepath.Join(t.TempDir(), "analytics.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	ctx := context.Background()
	result, err := db.ExecContext(ctx, `
		INSERT INTO incidents (
			incident_uuid, packet_hash, target_id_raw, region_code, province_code,
			municipality_code, barangay_code, zone_code, incident_type, channel,
			delivery_path, latitude, longitude, sender_device_id, event_timestamp, received_at
		) VALUES ('REAL-INC-1', 'REAL-HASH-1', '05-05-12-01-01::REAL', '05', '05',
			'12', '01', '01', 'FIRE', 'BLE_MESH', 'BLE_CLOUD_SHORTCUT', 13.1, 123.7,
			'REAL-DEVICE', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'))
	`)
	if err != nil {
		t.Fatal(err)
	}
	incidentID, _ := result.LastInsertId()
	result, err = db.ExecContext(ctx, `INSERT INTO mesh_packets (packet_uuid, packet_type, incident_id, origin_device_identifier, payload_hash) VALUES ('REAL-PACKET-1', 'SOS', ?, 'REAL-DEVICE', 'REAL-HASH-1')`, incidentID)
	if err != nil {
		t.Fatal(err)
	}
	packetID, _ := result.LastInsertId()
	for _, event := range []string{"RELAYED", "RELAYED", "UPLINKED", "DUPLICATE_DROPPED"} {
		if _, err := db.ExecContext(ctx, `INSERT INTO mesh_delivery_events (packet_id, device_identifier, event_type) VALUES (?, 'RELAY-DEVICE', ?)`, packetID, event); err != nil {
			t.Fatal(err)
		}
	}

	insights, err := NewAnalyticsRepo(db).GetTelemetryInsights(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if insights.TotalIncidents != 1 || insights.TotalPackets != 1 || insights.TotalDeliveryEvents != 4 {
		t.Fatalf("expected only operational data, got incidents=%d packets=%d events=%d", insights.TotalIncidents, insights.TotalPackets, insights.TotalDeliveryEvents)
	}
	if insights.MeshDeliveryRate == nil || *insights.MeshDeliveryRate != 100 || insights.AverageRelayHops == nil || *insights.AverageRelayHops != 2 {
		t.Fatalf("unexpected mesh calculations: rate=%v hops=%v", insights.MeshDeliveryRate, insights.AverageRelayHops)
	}
	if insights.RelayedPackets != 1 || insights.UniqueRelayDevices != 1 || insights.DuplicateDrops != 1 {
		t.Fatalf("unexpected event counts: %+v", insights)
	}
	if len(insights.IncidentTypeDistribution) != 1 || insights.IncidentTypeDistribution[0].Label != "FIRE" {
		t.Fatalf("unexpected incident distribution: %+v", insights.IncidentTypeDistribution)
	}
}
