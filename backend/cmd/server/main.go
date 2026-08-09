package main

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/config"
	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/handler"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/cache"
	"github.com/travelingsalesman/agap/backend/internal/platform/database"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
	"github.com/travelingsalesman/agap/backend/internal/service"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

func main() {
	// Configure structured logger
	slogHandler := slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})
	slog.SetDefault(slog.New(slogHandler))

	slog.Info("Starting AGAP Backend Engine...")

	// 1. Load Configuration
	cfg, err := config.Load()
	if err != nil {
		slog.Error("Failed to load configuration", "error", err)
		os.Exit(1)
	}

	// 2. Initialize Database Container (Factory loads SQLite or PostgreSQL)
	dbContainer, err := database.NewContainer(cfg)
	if err != nil {
		slog.Error("Failed to initialize database container", "error", err)
		os.Exit(1)
	}
	defer dbContainer.DB.Close()
	if cfg.Env == "production" {
		if err := disableDefaultSeedAccounts(context.Background(), dbContainer.DB); err != nil {
			slog.Error("Failed to disable default development accounts", "error", err)
			os.Exit(1)
		}
	}
	slog.Info("Database initialized successfully", "type", cfg.DBType)

	// 3. Initialize In-Memory TTL Cache for Telemetry Hash Dedup
	dedupCache := cache.NewTTLCache(cfg.DedupTTL())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go dedupCache.StartCleanup(ctx, time.Minute)

	// 4. Initialize WebSocket Hub
	wsHub := websocket.NewHub()
	go wsHub.Run()

	// 5. Initialize Services
	authSvc := service.NewAuthService(dbContainer.UserRepo, cfg.JWTSecret, dbContainer.AuditRepo)
	telemetrySvc := service.NewTelemetryService(dbContainer.TelemetryRepo, dbContainer.NotificationRepo, dbContainer.AuditRepo, dedupCache, wsHub)
	agencySvc := service.NewAgencyService(dbContainer.AgencyRepo, wsHub)
	dispatchSvc := service.NewDispatchService(dbContainer.DispatchRepo, dbContainer.TelemetryRepo, dbContainer.AgencyRepo, dbContainer.NotificationRepo, dbContainer.AuditRepo, wsHub)
	notificationSvc := service.NewNotificationService(dbContainer.NotificationRepo)
	geoSvc := service.NewGeoService(dbContainer.GeoRepo)
	messageSvc := service.NewMessageService(dbContainer.MessageRepo, wsHub)
	emergencyTypeSvc := service.NewEmergencyTypeService(dbContainer.EmergencyTypeRepo, wsHub)
	analyticsSvc := service.NewAnalyticsService(dbContainer.AnalyticsRepo)
	reportRepo := sqlite.NewReportRepository(dbContainer.DB)
	reportSvc := service.NewReportService(reportRepo, dbContainer.AgencyRepo, telemetrySvc, wsHub)
	syncSvc := service.NewSyncService(dbContainer.UserRepo, dbContainer.AgencyRepo, dbContainer.EmergencyTypeRepo, dbContainer.MessageRepo, dbContainer.TelemetryRepo, dbContainer.DispatchRepo, telemetrySvc)

	// Demo accounts are convenient for local development. Production startup
	// automatically suspends any account that still has the exact shipped demo
	// credentials, and runtime demo seeding remains development-only.
	if cfg.Env != "production" {
		seedInitialUsers(ctx, authSvc)
	}

	// 6. Initialize HTTP Handlers
	authH := handler.NewAuthHandler(authSvc)
	telemetryH := handler.NewTelemetryHandler(telemetrySvc)
	reportH := handler.NewReportHandler(reportSvc)
	incidentH := handler.NewIncidentHandler(telemetrySvc, dispatchSvc, agencySvc)
	agencyH := handler.NewAgencyHandler(agencySvc)
	responderH := handler.NewResponderHandler(agencySvc, dispatchSvc)
	notificationH := handler.NewNotificationHandler(notificationSvc)
	geoH := handler.NewGeoHandler(geoSvc)
	messageH := handler.NewMessageHandler(messageSvc)
	emergencyTypeH := handler.NewEmergencyTypeHandler(emergencyTypeSvc)
	analyticsH := handler.NewAnalyticsHandler(analyticsSvc)
	syncH := handler.NewSyncHandler(syncSvc)
	healthH := handler.NewHealthHandler(dbContainer.TelemetryRepo, cfg, dbContainer.DB)
	wsH := handler.NewWSHandler(wsHub, authSvc, cfg.CORSAllowedOrigins)
	auditH := handler.NewAuditHandler(dbContainer.AuditRepo)

	// 7. Setup Router & Middleware Stack
	r := chi.NewRouter()
	rateLimiter := middleware.NewRateLimiter(cfg.RateLimitRPS, cfg.RateLimitBurst)

	r.Use(middleware.Recoverer)
	r.Use(middleware.Logger)
	r.Use(middleware.CORS(cfg.CORSAllowedOrigins))
	r.Use(rateLimiter.Middleware)
	r.Use(middleware.BodyLimit(cfg.MaxRequestBodyBytes))

	// Public health/info endpoints expose only operational metadata. The database
	// explorer is a development tool and is disabled by default in production.
	r.Get("/health", healthH.Health)
	r.Get("/health/info", healthH.Info)
	r.Get("/api/v1/info", healthH.Info)
	r.Get("/api/v1/system/info", healthH.Info)
	if cfg.EnableSystemExplorer {
		r.Get("/health/ui", healthH.DashboardUI)
		r.Get("/info", healthH.DashboardUI)
		r.Get("/info/ui", healthH.DashboardUI)
		r.Get("/system/dashboard", healthH.DashboardUI)
		r.Get("/api/v1/system/db/tables", healthH.ListTables)
		r.Get("/api/v1/system/db/tables/{tableName}", healthH.GetTableData)
	}
	r.Get("/ws", wsH.ServeHTTP)

	// Public Auth Endpoints
	r.Post("/api/v1/auth/login", authH.Login)
	r.Post("/api/v1/auth/register", authH.Register)
	r.Post("/api/v1/auth/refresh", authH.RefreshToken)

	// Public Geo Hierarchy Lookups
	r.Get("/api/v1/geo/provinces", geoH.ListProvinces)
	r.Get("/api/v1/geo/municipalities", geoH.ListMunicipalities)
	r.Get("/api/v1/geo/barangays", geoH.ListBarangays)
	r.Get("/api/v1/geo/addresses", geoH.ListAddresses)

	// Protected Routes (JWT Required)
	jwtMiddleware := middleware.JWTAuth(authSvc)
	hmacMiddleware := middleware.HMACVerify(cfg.AESSecretKey, cfg.RequireTelemetryHMAC)
	incidentReadAccess := middleware.IncidentReadAccess(telemetrySvc)

	adminOnly := middleware.RequireRole(domain.RoleAdmin)
	respondersAndAdmins := middleware.RequireRole(domain.RoleAdmin, domain.RoleResponder)

	r.Route("/api/v1", func(r chi.Router) {
		r.Use(jwtMiddleware)

		// Incident Reports
		r.Post("/incident-reports", reportH.CreateReport)
		r.Get("/users/me/incident-reports", reportH.GetMyReports)
		r.Get("/users/me/incident-reports/{uuid}", reportH.GetMyReportByUUID)
		r.With(respondersAndAdmins).Get("/incidents/{id}/reports", reportH.GetIncidentReports)

		// Auth & User Profile
		r.Post("/auth/logout", authH.Logout)
		r.Get("/users/me", authH.GetProfile)
		r.Put("/users/me", authH.UpdateProfile)
		r.Get("/users/me/devices", authH.ListUserDevices)
		r.Post("/users/me/devices", authH.AddUserDevice)
		r.Delete("/users/me/devices/{id}", authH.DeleteUserDevice)
		r.Get("/users/me/contacts", authH.ListEmergencyContacts)
		r.Post("/users/me/contacts", authH.AddEmergencyContact)
		r.Put("/users/me/contacts/{id}", authH.UpdateEmergencyContact)
		r.Put("/users/me/contacts/{id}/primary", authH.SetPrimaryEmergencyContact)
		r.Delete("/users/me/contacts/{id}", authH.DeleteEmergencyContact)

		// Admin User Management
		r.With(adminOnly).Get("/users", authH.ListUsers)
		r.With(adminOnly).Get("/admin/users/{id}", authH.GetUserByID)
		r.With(adminOnly).Put("/admin/users/verify", authH.VerifyUser)
		r.With(adminOnly).Put("/admin/users/{id}/verify", authH.VerifyUser)
		r.With(adminOnly).Put("/admin/users/{id}/status", authH.UpdateUserAccountStatus)
		r.With(adminOnly).Put("/admin/users/{id}", authH.UpdateUserByID)
		r.With(adminOnly).Delete("/admin/users/{id}", authH.DeleteUser)
		r.With(adminOnly).Get("/admin/audit-logs", auditH.List)
		r.With(adminOnly).Post("/admin/messages/catalog", messageH.CreateCatalog)
		r.With(adminOnly).Put("/admin/messages/catalog/{id}", messageH.UpdateCatalog)
		r.With(adminOnly).Delete("/admin/messages/catalog/{id}", messageH.DeleteCatalog)
		r.With(adminOnly).Post("/admin/emergency-types", emergencyTypeH.Create)
		r.With(adminOnly).Put("/admin/emergency-types/{id}", emergencyTypeH.Update)
		r.With(adminOnly).Delete("/admin/emergency-types/{id}", emergencyTypeH.Delete)
		r.With(adminOnly).Post("/admin/rescue-units", responderH.Create)
		r.With(adminOnly).Put("/admin/rescue-units/{id}", responderH.Update)
		r.With(adminOnly).Post("/admin/rescue-units/{id}/members", responderH.AddMember)
		r.With(adminOnly).Delete("/admin/rescue-units/{id}/members/{responderId}", responderH.RemoveMember)

		// BLE predefined message catalog
		r.Get("/messages/catalog", messageH.ListCatalog)
		r.Get("/emergency-types", emergencyTypeH.List)

		// Telemetry Uplink (Requires JWT + HMAC signature for packet integrity)
		r.With(hmacMiddleware).Post("/telemetry/uplink", telemetryH.Uplink)
		r.Post("/mesh/events/batch", telemetryH.RecordMeshEventsBatch)
		r.With(respondersAndAdmins).Get("/analytics/telemetry", analyticsH.TelemetryInsights)
		r.With(respondersAndAdmins).Get("/reports/operations", analyticsH.OperationalReport)
		r.Get("/sync/bootstrap", syncH.Bootstrap)
		r.Post("/sync/push", syncH.Push)
		r.Get("/sync/changes", syncH.Changes)

		// Incidents Queries, Detail & Status Updates
		r.Get("/incidents", incidentH.List)
		r.With(incidentReadAccess).Get("/incidents/{id}", incidentH.GetByID)
		r.With(respondersAndAdmins).Put("/incidents/{id}/status", incidentH.UpdateStatus)
		r.With(respondersAndAdmins).Post("/incidents/{id}/dispatch", incidentH.Dispatch)
		r.With(incidentReadAccess).Get("/incidents/{id}/dispatches", incidentH.ListDispatches)
		r.With(incidentReadAccess).Get("/incidents/dispatches", incidentH.ListDispatchesBulk)
		r.With(respondersAndAdmins).Put("/incidents/{id}/dispatches/{dispatchID}/status", incidentH.UpdateDispatchStatus)
		r.With(incidentReadAccess).Get("/incidents/{id}/status-history", incidentH.ListStatusHistory)
		r.With(incidentReadAccess).Get("/incidents/status-history", incidentH.ListStatusHistoryBulk)
		r.With(incidentReadAccess).Get("/incidents/{id}/messages", messageH.ListIncidentMessages)
		r.With(respondersAndAdmins).Post("/incidents/{id}/messages", messageH.CreateIncidentMessage)

		// Notifications
		r.Get("/notifications", notificationH.List)
		r.Put("/notifications/{id}/read", notificationH.MarkRead)

		// Target ID Registry: Dispatch Agencies CRUD
		r.Route("/agencies", func(r chi.Router) {
			r.Get("/", agencyH.List)
			r.Get("/{id}", agencyH.GetByID)
			r.With(adminOnly).Post("/", agencyH.Create)
			r.With(adminOnly).Put("/{id}", agencyH.Update)
			r.With(adminOnly).Delete("/{id}", agencyH.Delete)
		})

		// Target ID Registry: Responder Units CRUD & GPS tracking
		r.Route("/responders", func(r chi.Router) {
			r.With(respondersAndAdmins).Get("/", responderH.List)
			r.Get("/me", responderH.GetMyResponderUnit)
			r.Get("/me/dispatches", responderH.ListMyDispatches)
			r.With(respondersAndAdmins).Get("/{id}", responderH.GetByID)
			r.With(adminOnly).Get("/{id}/dispatches", responderH.ListDispatches)
			r.With(respondersAndAdmins).Put("/{id}/location", responderH.UpdateLocation)
			r.With(adminOnly).Post("/", responderH.Create)
			r.With(adminOnly).Put("/{id}", responderH.Update)
			r.With(adminOnly).Delete("/{id}", responderH.Delete)
		})

		// Target rescue-unit route aliases. Existing /responders routes remain for mobile compatibility.
		r.Route("/rescue-units", func(r chi.Router) {
			r.With(respondersAndAdmins).Get("/", responderH.List)
			r.Get("/me", responderH.GetMyResponderUnit)
			r.Get("/me/dispatches", responderH.ListMyDispatches)
			r.With(respondersAndAdmins).Get("/{id}", responderH.GetByID)
			r.With(respondersAndAdmins).Get("/{id}/dispatches", responderH.ListDispatches)
			r.With(adminOnly).Get("/{id}/members", responderH.ListMembers)
			r.With(respondersAndAdmins).Put("/{id}/location", responderH.UpdateLocation)
			r.With(adminOnly).Post("/", responderH.Create)
			r.With(adminOnly).Put("/{id}", responderH.Update)
			r.With(adminOnly).Delete("/{id}", responderH.Delete)
			r.With(adminOnly).Post("/{id}/members", responderH.AddMember)
			r.With(adminOnly).Delete("/{id}/members/{responderId}", responderH.RemoveMember)
		})
	})

	// Serve static frontend SPA directly from Go backend if staticDir is present
	if cfg.StaticDir != "" {
		if info, err := os.Stat(cfg.StaticDir); err == nil && info.IsDir() {
			fileServer := http.FileServer(http.Dir(cfg.StaticDir))
			r.Handle("/*", http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
				path := cfg.StaticDir + req.URL.Path
				if info, err := os.Stat(path); os.IsNotExist(err) || info.IsDir() {
					http.ServeFile(w, req, cfg.StaticDir+"/index.html")
					return
				}
				fileServer.ServeHTTP(w, req)
			}))
			slog.Info("Go backend serving static frontend SPA directly", "dir", cfg.StaticDir)
		}
	}

	// 8. Start HTTP Server
	serverAddr := fmt.Sprintf("%s:%s", cfg.Host, cfg.Port)
	server := &http.Server{
		Addr:              serverAddr,
		Handler:           r,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
		ReadHeaderTimeout: 10 * time.Second,
		MaxHeaderBytes:    1 << 20,
	}

	go func() {
		slog.Info(fmt.Sprintf("AGAP Backend listening on http://%s", serverAddr))
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("HTTP Server error", "error", err)
			os.Exit(1)
		}
	}()

	// Graceful Shutdown
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop

	slog.Info("Shutting down AGAP Backend gracefully...")
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		slog.Error("Server forced shutdown error", "error", err)
	}

	slog.Info("AGAP Backend stopped cleanly.")
}

func disableDefaultSeedAccounts(ctx context.Context, db *sql.DB) error {
	if db == nil {
		return nil
	}

	// Migration 021 intentionally keeps convenient demo identities for local
	// development. In production, suspend only accounts that still have the exact
	// shipped username + password hash. A deliberately changed password is left alone.
	result, err := db.ExecContext(ctx, `
		UPDATE users
		SET account_status = 'SUSPENDED', is_verified = 0, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
		WHERE (username = 'admin' AND password_hash = '$2a$10$.qmQtqkhx..iPxr8xTyixOO8yQCSW3eb6Sr8vz5pgqGh/SolUNXMC')
		   OR (username IN ('responder1','responder2','responder3','responder4') AND password_hash = '$2a$10$xShIJyZ6bR2gYZ6C8oWvRuw00UtXY3b9XxcW5I92jNSf5OcwJeCju')
		   OR (username = 'citizen1' AND password_hash = '$2a$10$8VO8Tubfzy.AXLcSmm3DQu5z0AODW8k2ABTKoxXMkikO21RWrXZ.m')
	`)
	if err != nil {
		return fmt.Errorf("suspend shipped seed accounts: %w", err)
	}

	if _, err := db.ExecContext(ctx, `
		UPDATE refresh_tokens
		SET revoked = 1
		WHERE user_id IN (
			SELECT id FROM users
			WHERE account_status = 'SUSPENDED'
			  AND username IN ('admin','responder1','responder2','responder3','responder4','citizen1')
		)
	`); err != nil {
		return fmt.Errorf("revoke shipped seed sessions: %w", err)
	}

	if affected, err := result.RowsAffected(); err == nil && affected > 0 {
		slog.Warn("Disabled shipped development accounts for production", "accounts", affected)
	}
	return nil
}

func seedInitialUsers(ctx context.Context, authSvc service.AuthService) {
	if adminUser, err := authSvc.RegisterUser(ctx, "admin", "admin123", domain.RoleAdmin, "admin-device-001", nil, "System Administrator", "", "", "+63 52 742-0001", "admin@agap.gov.ph", "", "AGAP-ADM-001"); err == nil {
		_ = authSvc.VerifyUserAccount(ctx, adminUser.ID)
		slog.Info("Seeded initial default admin account (username: admin, verified: true)")
	}
	agencyId := int64(101)
	rescueUnitID := int64(1)
	if respUser, err := authSvc.RegisterUser(ctx, "responder", "responder123", domain.RoleResponder, "responder-device-001", &agencyId, "Officer Juan Dela Cruz", "", "", "+63 52 742-0003", "j.delacruz@bfp.gov.ph", "First Aid Certified", "BFP-RES-202", &rescueUnitID); err == nil {
		_ = authSvc.VerifyUserAccount(ctx, respUser.ID)
		slog.Info("Seeded initial default responder account (username: responder, verified: true)")
	}
	if citUser, err := authSvc.RegisterUser(ctx, "citizen", "citizen123", domain.RoleCitizen, "citizen-device-001", nil, "Maria Santos", "", "", "+63 917 123 4567", "maria.santos@gmail.com", "Asthma", "PH-PHILSYS-8912"); err == nil {
		_ = authSvc.VerifyUserAccount(ctx, citUser.ID)
		slog.Info("Seeded initial default citizen account (username: citizen, verified: true)")
	}
}
