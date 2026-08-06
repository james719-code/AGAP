package main

import (
	"context"
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
	authSvc := service.NewAuthService(dbContainer.UserRepo, cfg.JWTSecret)
	telemetrySvc := service.NewTelemetryService(dbContainer.TelemetryRepo, dbContainer.NotificationRepo, dbContainer.AuditRepo, dedupCache, wsHub)
	agencySvc := service.NewAgencyService(dbContainer.AgencyRepo, wsHub)
	dispatchSvc := service.NewDispatchService(dbContainer.DispatchRepo, dbContainer.TelemetryRepo, dbContainer.AgencyRepo, dbContainer.NotificationRepo, dbContainer.AuditRepo, wsHub)
	notificationSvc := service.NewNotificationService(dbContainer.NotificationRepo)
	geoSvc := service.NewGeoService(dbContainer.GeoRepo)

	// Seed initial admin user if no users exist
	seedInitialUsers(ctx, authSvc)

	// 6. Initialize HTTP Handlers
	authH := handler.NewAuthHandler(authSvc)
	telemetryH := handler.NewTelemetryHandler(telemetrySvc)
	incidentH := handler.NewIncidentHandler(telemetrySvc, dispatchSvc)
	agencyH := handler.NewAgencyHandler(agencySvc)
	responderH := handler.NewResponderHandler(agencySvc)
	notificationH := handler.NewNotificationHandler(notificationSvc)
	geoH := handler.NewGeoHandler(geoSvc)
	healthH := handler.NewHealthHandler(dbContainer.TelemetryRepo)
	wsH := handler.NewWSHandler(wsHub, authSvc)
	dbAdminH := handler.NewDBAdminHandler(dbContainer.DB)

	// 7. Setup Router & Middleware Stack
	r := chi.NewRouter()
	rateLimiter := middleware.NewRateLimiter(cfg.RateLimitRPS, cfg.RateLimitBurst)

	r.Use(middleware.Logger)
	r.Use(middleware.CORS(cfg.CORSAllowedOrigins))
	r.Use(rateLimiter.Middleware)

	// Public Routes
	r.Get("/health", healthH.Health)
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
	hmacMiddleware := middleware.HMACVerify(cfg.AESSecretKey)

	adminOnly := middleware.RequireRole(domain.RoleAdmin)
	dispatchersAndAdmins := middleware.RequireRole(domain.RoleAdmin, domain.RoleDispatcher, domain.RoleResponder)

	r.Route("/api/v1", func(r chi.Router) {
		r.Use(jwtMiddleware)

		// Auth & User Profile
		r.Post("/auth/logout", authH.Logout)
		r.Get("/users/me", authH.GetProfile)
		r.Put("/users/me", authH.UpdateProfile)
		r.Get("/users/me/contacts", authH.ListEmergencyContacts)
		r.Post("/users/me/contacts", authH.AddEmergencyContact)
		r.Delete("/users/me/contacts/{id}", authH.DeleteEmergencyContact)

		// Admin User Management
		r.With(adminOnly).Get("/users", authH.ListUsers)
		r.With(adminOnly).Put("/admin/users/verify", authH.VerifyUser)
		r.With(adminOnly).Put("/admin/users/{id}/verify", authH.VerifyUser)
		r.With(adminOnly).Put("/admin/users/{id}", authH.UpdateUserByID)
		r.With(adminOnly).Delete("/admin/users/{id}", authH.DeleteUser)

		// DB Explorer (Secured under Admin JWT)
		r.With(adminOnly).Get("/admin/db", dbAdminH.ServeHTTP)
		r.With(adminOnly).Post("/admin/db/row/insert", dbAdminH.InsertRow)
		r.With(adminOnly).Post("/admin/db/row/update", dbAdminH.UpdateRow)
		r.With(adminOnly).Post("/admin/db/row/delete", dbAdminH.DeleteRow)
		r.With(adminOnly).Post("/admin/db/schema/create-table", dbAdminH.CreateTable)
		r.With(adminOnly).Post("/admin/db/schema/add-column", dbAdminH.AddColumn)
		r.With(adminOnly).Post("/admin/db/schema/drop-table", dbAdminH.DropTable)
		r.With(adminOnly).Get("/admin/db/tables", dbAdminH.GetTables)

		// Telemetry Uplink (Requires JWT + HMAC signature for packet integrity)
		r.With(hmacMiddleware).Post("/telemetry/uplink", telemetryH.Uplink)

		// Incidents Queries, Detail & Status Updates
		r.Get("/incidents", incidentH.List)
		r.Get("/incidents/{id}", incidentH.GetByID)
		r.With(dispatchersAndAdmins).Put("/incidents/{id}/status", incidentH.UpdateStatus)
		r.With(dispatchersAndAdmins).Post("/incidents/{id}/dispatch", incidentH.Dispatch)
		r.Get("/incidents/{id}/dispatches", incidentH.ListDispatches)

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
			r.Get("/", responderH.List)
			r.Get("/me", responderH.GetMyResponderUnit)
			r.Get("/{id}", responderH.GetByID)
			r.With(dispatchersAndAdmins).Put("/{id}/location", responderH.UpdateLocation)
			r.With(adminOnly).Post("/", responderH.Create)
			r.With(adminOnly).Put("/{id}", responderH.Update)
			r.With(adminOnly).Delete("/{id}", responderH.Delete)
		})
	})


	// 8. Start HTTP Server
	serverAddr := fmt.Sprintf("%s:%s", cfg.Host, cfg.Port)
	server := &http.Server{
		Addr:         serverAddr,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
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

func seedInitialUsers(ctx context.Context, authSvc service.AuthService) {
	if adminUser, err := authSvc.RegisterUser(ctx, "admin", "admin123", domain.RoleAdmin, "admin-device-001", nil, "System Administrator", "", "", "+63 52 742-0001", "admin@agap.gov.ph", "", "AGAP-ADM-001"); err == nil {
		_ = authSvc.VerifyUserAccount(ctx, adminUser.ID)
		slog.Info("Seeded initial default admin account (username: admin, verified: true)")
	}
	agencyId := int64(101)
	if respUser, err := authSvc.RegisterUser(ctx, "responder", "responder123", domain.RoleResponder, "responder-device-001", &agencyId, "Officer Juan Dela Cruz", "", "", "+63 52 742-0003", "j.delacruz@bfp.gov.ph", "First Aid Certified", "BFP-RES-202"); err == nil {
		_ = authSvc.VerifyUserAccount(ctx, respUser.ID)
		slog.Info("Seeded initial default responder account (username: responder, verified: true)")
	}
	if citUser, err := authSvc.RegisterUser(ctx, "citizen", "citizen123", domain.RoleCitizen, "citizen-device-001", nil, "Maria Santos", "", "", "+63 917 123 4567", "maria.santos@gmail.com", "Asthma", "PH-PHILSYS-8912"); err == nil {
		_ = authSvc.VerifyUserAccount(ctx, citUser.ID)
		slog.Info("Seeded initial default citizen account (username: citizen, verified: true)")
	}
}


