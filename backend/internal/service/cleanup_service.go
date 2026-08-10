package service

import (
	"context"
	"log/slog"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/config"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

type CleanupResult struct {
	MeshEventsPurged     int64 `json:"mesh_events_purged"`
	MeshPacketsPurged    int64 `json:"mesh_packets_purged"`
	RefreshTokensPurged int64 `json:"refresh_tokens_purged"`
	NotificationsPurged int64 `json:"notifications_purged"`
	AuditLogsPurged     int64 `json:"audit_logs_purged"`
	TotalPurged         int64 `json:"total_purged"`
}

type CleanupService interface {
	RunCleanup(ctx context.Context) (*CleanupResult, error)
	StartCronWorker(ctx context.Context)
}

type cleanupService struct {
	repo sqlite.CleanupRepository
	cfg  *config.Config
}

func NewCleanupService(repo sqlite.CleanupRepository, cfg *config.Config) CleanupService {
	return &cleanupService{
		repo: repo,
		cfg:  cfg,
	}
}

func (s *cleanupService) RunCleanup(ctx context.Context) (*CleanupResult, error) {
	now := time.Now().UTC()

	meshEventsCutoff := now.AddDate(0, 0, -s.cfg.MeshEventsRetentionDays)
	meshPacketsCutoff := now.AddDate(0, 0, -s.cfg.MeshPacketsRetentionDays)
	tokensCutoff := now.AddDate(0, 0, -s.cfg.RefreshTokensRetentionDays)
	notifCutoff := now.AddDate(0, 0, -s.cfg.NotificationsRetentionDays)
	auditCutoff := now.AddDate(0, 0, -s.cfg.AuditLogsRetentionDays)

	eventsCount, err := s.repo.PurgeOldMeshDeliveryEvents(ctx, meshEventsCutoff)
	if err != nil {
		slog.Error("Data cleanup: failed to purge mesh delivery events", "error", err)
	}

	packetsCount, err := s.repo.PurgeOldMeshPackets(ctx, meshPacketsCutoff)
	if err != nil {
		slog.Error("Data cleanup: failed to purge mesh packets", "error", err)
	}

	tokensCount, err := s.repo.PurgeExpiredRefreshTokens(ctx, tokensCutoff)
	if err != nil {
		slog.Error("Data cleanup: failed to purge refresh tokens", "error", err)
	}

	notifCount, err := s.repo.PurgeOldNotifications(ctx, notifCutoff)
	if err != nil {
		slog.Error("Data cleanup: failed to purge notifications", "error", err)
	}

	auditCount, err := s.repo.PurgeOldAuditLogs(ctx, auditCutoff)
	if err != nil {
		slog.Error("Data cleanup: failed to purge audit logs", "error", err)
	}

	total := eventsCount + packetsCount + tokensCount + notifCount + auditCount

	result := &CleanupResult{
		MeshEventsPurged:     eventsCount,
		MeshPacketsPurged:    packetsCount,
		RefreshTokensPurged: tokensCount,
		NotificationsPurged: notifCount,
		AuditLogsPurged:     auditCount,
		TotalPurged:         total,
	}

	if total > 0 {
		slog.Info("Automated database maintenance cleanup completed",
			"mesh_events_purged", eventsCount,
			"mesh_packets_purged", packetsCount,
			"refresh_tokens_purged", tokensCount,
			"notifications_purged", notifCount,
			"audit_logs_purged", auditCount,
			"total_purged", total,
		)
	}

	return result, nil
}

func (s *cleanupService) StartCronWorker(ctx context.Context) {
	if !s.cfg.AutoCleanupEnabled {
		slog.Info("Data cleanup cron worker disabled by configuration")
		return
	}

	interval := time.Duration(s.cfg.CleanupIntervalMinutes) * time.Minute
	if interval < time.Minute {
		interval = 60 * time.Minute
	}

	slog.Info("Starting automated backend data cleanup cron worker", "interval_minutes", s.cfg.CleanupIntervalMinutes)

	// Run initial cleanup once after brief boot warm up
	go func() {
		time.Sleep(10 * time.Second)
		if ctx.Err() == nil {
			_, _ = s.RunCleanup(ctx)
		}
	}()

	ticker := time.NewTicker(interval)
	go func() {
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				slog.Info("Data cleanup cron worker shutting down")
				return
			case <-ticker.C:
				_, _ = s.RunCleanup(ctx)
			}
		}
	}()
}
