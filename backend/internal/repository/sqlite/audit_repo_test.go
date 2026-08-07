package sqlite_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

func TestAuditRepo_ListAuditLogsFilterModes(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_audit.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	rows := []struct {
		action     string
		entityType string
		entityID   int64
		createdAt  string
	}{
		{"USER_APPROVED", "USER", 101, "2026-08-08T01:00:00Z"},
		{"USER_SUSPENDED", "USER", 102, "2026-08-08T02:00:00Z"},
		{"DISPATCH_ASSIGNED", "DISPATCH", 201, "2026-08-08T03:00:00Z"},
	}
	for _, row := range rows {
		_, err := db.ExecContext(ctx, `
			INSERT INTO audit_logs (action, entity_type, entity_id, old_value, new_value, created_at)
			VALUES (?, ?, ?, '', '', ?)
		`, row.action, row.entityType, row.entityID, row.createdAt)
		if err != nil {
			t.Fatalf("failed to seed audit log %s: %v", row.action, err)
		}
	}

	repo := sqlite.NewAuditRepo(db)

	allLogs, err := repo.ListAuditLogs(ctx, repository.AuditLogFilter{Limit: 2})
	if err != nil {
		t.Fatalf("failed to list all audit logs: %v", err)
	}
	if len(allLogs) != 2 {
		t.Fatalf("expected limited all logs length 2, got %d", len(allLogs))
	}
	if allLogs[0].Action != "DISPATCH_ASSIGNED" || allLogs[1].Action != "USER_SUSPENDED" {
		t.Fatalf("expected newest all logs first, got %+v", allLogs)
	}

	userLogs, err := repo.ListAuditLogs(ctx, repository.AuditLogFilter{EntityType: "USER", Limit: 10})
	if err != nil {
		t.Fatalf("failed to list user audit logs: %v", err)
	}
	if len(userLogs) != 2 || userLogs[0].EntityType != "USER" || userLogs[1].EntityType != "USER" {
		t.Fatalf("expected only USER audit logs, got %+v", userLogs)
	}
	if userLogs[0].Action != "USER_SUSPENDED" || userLogs[1].Action != "USER_APPROVED" {
		t.Fatalf("expected newest USER logs first, got %+v", userLogs)
	}

	specificLogs, err := repo.ListAuditLogs(ctx, repository.AuditLogFilter{EntityType: "USER", EntityID: 101, Limit: 10})
	if err != nil {
		t.Fatalf("failed to list entity audit logs: %v", err)
	}
	if len(specificLogs) != 1 || specificLogs[0].Action != "USER_APPROVED" {
		t.Fatalf("expected specific USER audit log, got %+v", specificLogs)
	}

	actionLogs, err := repo.ListAuditLogs(ctx, repository.AuditLogFilter{Action: "DISPATCH_ASSIGNED", Limit: 10})
	if err != nil {
		t.Fatalf("failed to list action audit logs: %v", err)
	}
	if len(actionLogs) != 1 || actionLogs[0].EntityType != "DISPATCH" {
		t.Fatalf("expected dispatch action audit log, got %+v", actionLogs)
	}

	datedLogs, err := repo.ListAuditLogs(ctx, repository.AuditLogFilter{DateFrom: "2026-08-08T01:30:00Z", DateTo: "2026-08-08T02:30:00Z", Limit: 10})
	if err != nil {
		t.Fatalf("failed to list dated audit logs: %v", err)
	}
	if len(datedLogs) != 1 || datedLogs[0].Action != "USER_SUSPENDED" {
		t.Fatalf("expected dated USER_SUSPENDED audit log, got %+v", datedLogs)
	}
}
