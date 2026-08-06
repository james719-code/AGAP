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
	telemetrySvc := service.NewTelemetryService(dbContainer.TelemetryRepo, dedupCache, wsHub)
	agencySvc := service.NewAgencyService(dbContainer.AgencyRepo, wsHub)

	// Seed initial admin user if no users exist
	seedInitialUsers(ctx, authSvc)

	// 6. Initialize HTTP Handlers
	authH := handler.NewAuthHandler(authSvc)
	telemetryH := handler.NewTelemetryHandler(telemetrySvc)
	incidentH := handler.NewIncidentHandler(telemetrySvc)
	agencyH := handler.NewAgencyHandler(agencySvc)
	responderH := handler.NewResponderHandler(agencySvc)
	healthH := handler.NewHealthHandler(dbContainer.TelemetryRepo)
	wsH := handler.NewWSHandler(wsHub, authSvc)

	// 7. Setup Router & Middleware Stack
	r := chi.NewRouter()
	rateLimiter := middleware.NewRateLimiter(cfg.RateLimitRPS, cfg.RateLimitBurst)

	r.Use(middleware.Logger)
	r.Use(middleware.CORS(cfg.CORSAllowedOrigins))
	r.Use(rateLimiter.Middleware)

	// Public Routes
	r.Get("/health", healthH.Health)
	r.Get("/ws", wsH.ServeHTTP)
	r.Post("/api/v1/auth/login", authH.Login)
	r.Post("/api/v1/auth/register", authH.Register)

	// Protected Routes (JWT Required)
	jwtMiddleware := middleware.JWTAuth(authSvc)
	hmacMiddleware := middleware.HMACVerify(cfg.AESSecretKey)

	r.Route("/api/v1", func(r chi.Router) {
		r.Use(jwtMiddleware)

		// Telemetry Uplink (Requires JWT + HMAC signature for packet integrity)
		r.With(hmacMiddleware).Post("/telemetry/uplink", telemetryH.Uplink)

		// Incidents Queries & Status Updates
		r.Get("/incidents", incidentH.List)
		r.Put("/incidents/{id}/status", incidentH.UpdateStatus)

		// Target ID Registry: Dispatch Agencies CRUD
		r.Route("/agencies", func(r chi.Router) {
			r.Get("/", agencyH.List)
			r.Post("/", agencyH.Create)
			r.Get("/{id}", agencyH.GetByID)
			r.Put("/{id}", agencyH.Update)
			r.Delete("/{id}", agencyH.Delete)
		})

		// Target ID Registry: Responder Units CRUD & GPS tracking
		r.Route("/responders", func(r chi.Router) {
			r.Get("/", responderH.List)
			r.Post("/", responderH.Create)
			r.Get("/{id}", responderH.GetByID)
			r.Put("/{id}", responderH.Update)
			r.Put("/{id}/location", responderH.UpdateLocation)
			r.Delete("/{id}", responderH.Delete)
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
	if _, err := authSvc.RegisterUser(ctx, "admin", "admin123", domain.RoleAdmin, "admin-device-001", nil); err == nil {
		slog.Info("Seeded initial default admin account (username: admin, display_id: 1-1)")
	}
	agencyId := int64(101)
	if _, err := authSvc.RegisterUser(ctx, "responder", "responder123", domain.RoleResponder, "responder-device-001", &agencyId); err == nil {
		slog.Info("Seeded initial default responder account (username: responder, display_id: 2-2)")
	}
	if _, err := authSvc.RegisterUser(ctx, "citizen", "citizen123", domain.RoleCitizen, "citizen-device-001", nil); err == nil {
		slog.Info("Seeded initial default citizen account (username: citizen, display_id: 3-3)")
	}
}

