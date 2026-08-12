package sqlite_test

import (
	"context"
	"database/sql"
	"errors"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

func TestMessageRepo_CatalogSchemaAndCRUD(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_messages.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	for _, column := range []string{"message_en", "message_fil", "message_bikol", "message_type", "is_active", "updated_at"} {
		if !tableHasColumn(t, db, "predefined_messages", column) {
			t.Fatalf("expected predefined_messages.%s column", column)
		}
	}
	if tableHasColumn(t, db, "predefined_messages", "message_text") {
		t.Fatalf("expected legacy predefined_messages.message_text to be retired")
	}

	repo := sqlite.NewMessageRepo(db)
	activeMessages, err := repo.ListPredefinedMessages(ctx, false)
	if err != nil {
		t.Fatalf("failed to list active catalog messages: %v", err)
	}
	if len(activeMessages) == 0 {
		t.Fatalf("expected seeded catalog messages")
	}
	if activeMessages[0].MessageEN == "" {
		t.Fatalf("expected seeded message_en text")
	}

	emergencyTypeID := int64(1)
	created, err := repo.CreatePredefinedMessage(ctx, &domain.PredefinedMessage{
		Code:            "TEST_MEDICAL_ACK",
		MessageEN:       "Medical team acknowledged.",
		MessageFil:      "Kinumpirma ng medical team.",
		MessageBikol:    "Kinumpirmar kan medical team.",
		MessageType:     domain.PredefinedMessageTypeAck,
		EmergencyTypeID: &emergencyTypeID,
		IsActive:        true,
	})
	if err != nil {
		t.Fatalf("failed to create catalog message: %v", err)
	}
	if created.ID == 0 || created.Code != "TEST_MEDICAL_ACK" {
		t.Fatalf("unexpected created message: %+v", created)
	}

	created.MessageEN = "Medical team is on the way."
	created.MessageType = domain.PredefinedMessageTypeStatus
	created.IsActive = false
	updated, err := repo.UpdatePredefinedMessage(ctx, created)
	if err != nil {
		t.Fatalf("failed to update catalog message: %v", err)
	}
	if updated.MessageEN != "Medical team is on the way." || updated.MessageType != domain.PredefinedMessageTypeStatus || updated.IsActive {
		t.Fatalf("unexpected updated message: %+v", updated)
	}

	activeMessages, err = repo.ListPredefinedMessages(ctx, false)
	if err != nil {
		t.Fatalf("failed to list active catalog messages after update: %v", err)
	}
	for _, message := range activeMessages {
		if message.ID == updated.ID {
			t.Fatalf("inactive message should not be returned by default")
		}
	}

	// Delete removes the record entirely (second delete must report not found).
	if err := repo.DeletePredefinedMessage(ctx, updated.ID); err != nil {
		t.Fatalf("failed to delete catalog message: %v", err)
	}
	if err := repo.DeletePredefinedMessage(ctx, updated.ID); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("expected ErrNotFound after delete, got %v", err)
	}
}

func TestMessageRepo_IncidentMessages(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_incident_messages.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	if !tableHasColumn(t, db, "incident_messages", "predefined_message_id") {
		t.Fatalf("expected incident_messages table to be migrated")
	}

	ctx := context.Background()
	now := "2026-01-01T00:00:00Z"
	userRes, err := db.ExecContext(ctx, `
		INSERT INTO users (username, password_hash, role, account_status, is_verified, created_at, updated_at)
		VALUES ('incident-message-admin', 'hash', 'ADMIN', 'APPROVED', 1, ?, ?)
	`, now, now)
	if err != nil {
		t.Fatalf("failed to insert user: %v", err)
	}
	userID, _ := userRes.LastInsertId()

	incidentRes, err := db.ExecContext(ctx, `
		INSERT INTO incidents (
			incident_uuid, packet_hash, target_id_raw, region_code, province_code, municipality_code,
			barangay_code, zone_code, incident_type, channel, delivery_path, latitude, longitude,
			altitude, gps_accuracy, hop_count, ttl, sender_device_id, status, event_timestamp, received_at,
			created_at, updated_at
		)
		VALUES (
			'incident-message-test', 'incident-message-hash', '05-05-12-01-04::incident-message-test',
			'05', '05', '12', '01', '04', 'MEDICAL', 'ONLINE', 'DIRECT_UPLINK',
			13.1391, 123.7438, 0, 5, 0, 10, 'device-incident-message', 'RECEIVED', ?, ?, ?, ?
		)
	`, now, now, now, now)
	if err != nil {
		t.Fatalf("failed to insert incident: %v", err)
	}
	incidentID, _ := incidentRes.LastInsertId()

	repo := sqlite.NewMessageRepo(db)
	senderID := userID
	custom, err := repo.CreateIncidentMessage(ctx, &domain.IncidentMessage{
		IncidentID:      incidentID,
		SenderUserID:    &senderID,
		CustomMessage:   "Responder is reviewing the incident.",
		Channel:         domain.IncidentMessageChannelOnline,
		ReceiverUserID:  nil,
		ClientMessageID: "android-local-1",
	})
	if err != nil {
		t.Fatalf("failed to create custom incident message: %v", err)
	}
	if custom.ID == 0 || custom.SenderName != "incident-message-admin" {
		t.Fatalf("unexpected custom incident message: %+v", custom)
	}
	if custom.SenderRole != "ADMIN" || custom.MessageType != domain.IncidentMessageTypeChat {
		t.Fatalf("custom message missing canonical presentation metadata: %+v", custom)
	}
	if custom.LegacySenderID != senderID || custom.MessageContent != custom.CustomMessage || !custom.CreatedAt.Equal(custom.SentAt) {
		t.Fatalf("custom message missing Android compatibility aliases: %+v", custom)
	}

	// Retrying the same Android client message must return the existing row,
	// even if the caller repeats the POST after losing the first response.
	retried, err := repo.CreateIncidentMessage(ctx, &domain.IncidentMessage{
		IncidentID:      incidentID,
		SenderUserID:    &senderID,
		CustomMessage:   "Responder is reviewing the incident.",
		Channel:         domain.IncidentMessageChannelOnline,
		ClientMessageID: "android-local-1",
	})
	if err != nil {
		t.Fatalf("idempotent retry failed: %v", err)
	}
	if retried.ID != custom.ID || retried.ClientMessageID != "android-local-1" {
		t.Fatalf("retry should resolve the original row: first=%+v retry=%+v", custom, retried)
	}

	predefinedID := int64(2)
	if _, err := repo.CreateIncidentMessage(ctx, &domain.IncidentMessage{
		IncidentID:          incidentID,
		SenderUserID:        &senderID,
		PredefinedMessageID: &predefinedID,
		Channel:             domain.IncidentMessageChannelBLE,
	}); err != nil {
		t.Fatalf("failed to create predefined incident message: %v", err)
	}

	messages, err := repo.ListIncidentMessages(ctx, incidentID)
	if err != nil {
		t.Fatalf("failed to list incident messages: %v", err)
	}
	if len(messages) != 2 {
		t.Fatalf("expected two incident messages, got %d", len(messages))
	}
	if messages[0].CustomMessage == "" || messages[1].PredefinedMessageID == nil || messages[1].PredefinedCode == "" {
		t.Fatalf("incident messages missing expected metadata: %+v", messages)
	}
	if messages[1].MessageType != domain.IncidentMessageTypeStatus {
		t.Fatalf("predefined message should be a status update: %+v", messages[1])
	}
}

func TestMessageRepo_ResponderMessageAccessFollowsActiveMembership(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_message_access.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	now := "2026-01-01T00:00:00Z"
	insertUser := func(username string) int64 {
		res, err := db.ExecContext(ctx, `
			INSERT INTO users (username, password_hash, role, account_status, is_verified, created_at, updated_at)
			VALUES (?, 'hash', 'RESPONDER', 'APPROVED', 1, ?, ?)
		`, username, now, now)
		if err != nil {
			t.Fatalf("failed to insert responder %s: %v", username, err)
		}
		id, _ := res.LastInsertId()
		return id
	}
	responderA := insertUser("message-access-a")
	responderB := insertUser("message-access-b")

	var agencyID int64
	if err := db.QueryRowContext(ctx, `SELECT id FROM agencies ORDER BY id LIMIT 1`).Scan(&agencyID); err != nil {
		t.Fatalf("expected a seeded agency: %v", err)
	}

	unitRes, err := db.ExecContext(ctx, `
		INSERT INTO rescue_units (
			agency_id, user_id, target_id, unit_code, unit_type, responder_name,
			responder_phone, device_id, status, current_latitude, current_longitude,
			last_location_at, created_at, updated_at
		) VALUES (?, ?, '05-05-12-01-04', 'MSG-ACCESS-UNIT', 'RESCUE_VEHICLE',
			'Message Access Unit', '', 'device-message-access', 'IDLE', 13.13, 123.74, ?, ?, ?)
	`, agencyID, responderA, now, now, now)
	if err != nil {
		t.Fatalf("failed to create rescue unit: %v", err)
	}
	unitID, _ := unitRes.LastInsertId()

	incidentRes, err := db.ExecContext(ctx, `
		INSERT INTO incidents (
			incident_uuid, packet_hash, target_id_raw, region_code, province_code, municipality_code,
			barangay_code, zone_code, incident_type, channel, delivery_path, latitude, longitude,
			altitude, gps_accuracy, hop_count, ttl, sender_device_id, status, event_timestamp, received_at,
			created_at, updated_at
		) VALUES (
			'message-access-incident', 'message-access-hash', '05-05-12-01-04::message-access-incident',
			'05', '05', '12', '01', '04', 'MEDICAL', 'ONLINE', 'DIRECT_UPLINK',
			13.1391, 123.7438, 0, 5, 0, 10, 'device-message-access-source', 'DISPATCHED', ?, ?, ?, ?
		)
	`, now, now, now, now)
	if err != nil {
		t.Fatalf("failed to insert incident: %v", err)
	}
	incidentID, _ := incidentRes.LastInsertId()

	if _, err := db.ExecContext(ctx, `
		INSERT INTO incident_dispatches (incident_id, unit_id, status, dispatched_at)
		VALUES (?, ?, 'ASSIGNED', ?)
	`, incidentID, unitID, now); err != nil {
		t.Fatalf("failed to create dispatch: %v", err)
	}

	repo := sqlite.NewMessageRepo(db)
	allowed, err := repo.CanResponderAccessIncidentMessages(ctx, incidentID, responderA)
	if err != nil || !allowed {
		t.Fatalf("legacy leader should be allowed only while unit has no membership history: allowed=%v err=%v", allowed, err)
	}

	// Once the unit uses membership records, those records become authoritative.
	memberRes, err := db.ExecContext(ctx, `
		INSERT INTO rescue_unit_members (rescue_unit_id, responder_id, is_leader, assigned_at)
		VALUES (?, ?, 1, ?)
	`, unitID, responderA, now)
	if err != nil {
		t.Fatalf("failed to add active membership: %v", err)
	}
	memberID, _ := memberRes.LastInsertId()

	allowed, err = repo.CanResponderAccessIncidentMessages(ctx, incidentID, responderA)
	if err != nil || !allowed {
		t.Fatalf("active member should have incident message access: allowed=%v err=%v", allowed, err)
	}

	if _, err := db.ExecContext(ctx, `UPDATE rescue_unit_members SET removed_at = ? WHERE id = ?`, "2026-01-01T00:01:00Z", memberID); err != nil {
		t.Fatalf("failed to remove member: %v", err)
	}
	allowed, err = repo.CanResponderAccessIncidentMessages(ctx, incidentID, responderA)
	if err != nil {
		t.Fatalf("failed to re-check removed member: %v", err)
	}
	if allowed {
		t.Fatalf("removed member must not regain access through rescue_units.user_id legacy fallback")
	}

	if _, err := db.ExecContext(ctx, `
		INSERT INTO rescue_unit_members (rescue_unit_id, responder_id, is_leader, assigned_at)
		VALUES (?, ?, 1, '2026-01-01T00:02:00Z')
	`, unitID, responderB); err != nil {
		t.Fatalf("failed to add replacement member: %v", err)
	}
	allowed, err = repo.CanResponderAccessIncidentMessages(ctx, incidentID, responderB)
	if err != nil || !allowed {
		t.Fatalf("replacement active member should have access: allowed=%v err=%v", allowed, err)
	}

	recipients, err := repo.ListActiveIncidentResponderUserIDs(ctx, incidentID)
	if err != nil {
		t.Fatalf("failed to list realtime recipients: %v", err)
	}
	if len(recipients) != 1 || recipients[0] != responderB {
		t.Fatalf("expected only replacement responder in realtime recipients, got %v", recipients)
	}

	if _, err := db.ExecContext(ctx, `UPDATE incident_dispatches SET status = 'COMPLETED' WHERE incident_id = ? AND unit_id = ?`, incidentID, unitID); err != nil {
		t.Fatalf("failed to complete dispatch: %v", err)
	}
	allowed, err = repo.CanResponderAccessIncidentMessages(ctx, incidentID, responderB)
	if err != nil {
		t.Fatalf("failed to verify completed dispatch: %v", err)
	}
	if allowed {
		t.Fatalf("completed dispatch must revoke incident message access")
	}
}

func tableHasColumn(t *testing.T, db interface {
	Query(query string, args ...interface{}) (*sql.Rows, error)
}, table string, column string) bool {
	t.Helper()
	rows, err := db.Query("PRAGMA table_info(" + table + ")")
	if err != nil {
		t.Fatalf("failed to inspect %s: %v", table, err)
	}
	defer rows.Close()

	for rows.Next() {
		var cid int
		var name, columnType string
		var notNull int
		var defaultValue interface{}
		var pk int
		if err := rows.Scan(&cid, &name, &columnType, &notNull, &defaultValue, &pk); err != nil {
			t.Fatalf("failed to scan %s column info: %v", table, err)
		}
		if name == column {
			return true
		}
	}
	return false
}
