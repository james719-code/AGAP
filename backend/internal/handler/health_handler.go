package handler

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/travelingsalesman/agap/backend/internal/config"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

var startTime = time.Now()

type HealthHandler struct {
	telemetryRepo repository.TelemetryRepository
	cfg           *config.Config
	db            *sql.DB
}

func NewHealthHandler(telemetryRepo repository.TelemetryRepository, cfg *config.Config, db *sql.DB) *HealthHandler {
	return &HealthHandler{
		telemetryRepo: telemetryRepo,
		cfg:           cfg,
		db:            db,
	}
}

func (h *HealthHandler) Health(w http.ResponseWriter, r *http.Request) {
	dbStatus := "ok"
	if err := h.telemetryRepo.Ping(r.Context()); err != nil {
		dbStatus = "error: " + err.Error()
	}

	w.Header().Set("Content-Type", "application/json")
	if dbStatus != "ok" {
		w.WriteHeader(http.StatusServiceUnavailable)
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status": dbStatus,
		"db":     dbStatus,
		"uptime": time.Since(startTime).String(),
	})
}

type APIInfo struct {
	Name              string                 `json:"name"`
	Version           string                 `json:"version"`
	Environment       string                 `json:"environment"`
	ServerTime        string                 `json:"server_time"`
	Uptime            string                 `json:"uptime"`
	WebSocketEndpoint string                 `json:"websocket_endpoint"`
	RateLimiting      map[string]interface{} `json:"rate_limiting"`
}

type DatabaseInfo struct {
	Type       string           `json:"type"`
	FilePath   string           `json:"file_path,omitempty"`
	Status     string           `json:"status"`
	Ping       string           `json:"ping"`
	Statistics map[string]int64 `json:"statistics"`
}

type SystemInfoResponse struct {
	Status   string       `json:"status"`
	API      APIInfo      `json:"api"`
	Database DatabaseInfo `json:"database"`
}

func (h *HealthHandler) Info(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	dbPing := "ok"
	if err := h.telemetryRepo.Ping(ctx); err != nil {
		dbPing = "error: " + err.Error()
	}

	dbType := "sqlite"
	dbFilePath := "./agap_gis.db"
	envStr := "development"
	rps := 20.0
	burst := 40

	if h.cfg != nil {
		dbType = h.cfg.DBType
		dbFilePath = h.cfg.DBFilePath
		envStr = h.cfg.Env
		rps = h.cfg.RateLimitRPS
		burst = h.cfg.RateLimitBurst
		if h.cfg.Env == "production" {
			dbFilePath = ""
		}
	}

	stats := make(map[string]int64)
	exposeStats := h.cfg == nil || h.cfg.Env != "production"
	if h.db != nil && exposeStats {
		tables := []string{"users", "incidents", "agencies", "rescue_units", "emergency_types", "audit_logs"}
		for _, table := range tables {
			var count int64
			query := "SELECT COUNT(*) FROM " + table
			if err := h.db.QueryRowContext(ctx, query).Scan(&count); err == nil {
				stats[table] = count
			} else {
				stats[table] = 0
			}
		}
	}

	overallStatus := "ok"
	if dbPing != "ok" {
		overallStatus = "degraded"
	}

	resp := SystemInfoResponse{
		Status: overallStatus,
		API: APIInfo{
			Name:              "AGAP Telemetry & Dispatch Backend Engine",
			Version:           "1.0.0",
			Environment:       envStr,
			ServerTime:        time.Now().UTC().Format(time.RFC3339),
			Uptime:            time.Since(startTime).Truncate(time.Second).String(),
			WebSocketEndpoint: "/ws",
			RateLimiting: map[string]interface{}{
				"rps":   rps,
				"burst": burst,
			},
		},
		Database: DatabaseInfo{
			Type:       dbType,
			FilePath:   dbFilePath,
			Status:     dbPing,
			Ping:       dbPing,
			Statistics: stats,
		},
	}

	w.Header().Set("Content-Type", "application/json")
	if overallStatus != "ok" {
		w.WriteHeader(http.StatusMultiStatus)
	}
	_ = json.NewEncoder(w).Encode(resp)
}

type TableSummary struct {
	Name  string `json:"name"`
	Count int64  `json:"count"`
}

func (h *HealthHandler) ListTables(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	if h.db == nil {
		w.WriteHeader(http.StatusServiceUnavailable)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": "database connection unavailable"})
		return
	}

	ctx := r.Context()
	query := "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
	rows, err := h.db.QueryContext(ctx, query)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	// Collect table names first, then close the result set before running
	// COUNT(*) queries. This is required for the in-memory SQLite test DB,
	// which intentionally uses a single connection.
	var tableNames []string
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			_ = rows.Close()
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
			return
		}
		tableNames = append(tableNames, name)
	}
	if err := rows.Err(); err != nil {
		_ = rows.Close()
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	if err := rows.Close(); err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	tables := make([]TableSummary, 0, len(tableNames))
	for _, name := range tableNames {
		var count int64
		if err := h.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM "+name).Scan(&count); err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
			return
		}
		tables = append(tables, TableSummary{Name: name, Count: count})
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"tables": tables,
		"total":  len(tables),
	})
}

func (h *HealthHandler) GetTableData(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	if h.db == nil {
		w.WriteHeader(http.StatusServiceUnavailable)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": "database connection unavailable"})
		return
	}

	tableName := chi.URLParam(r, "tableName")
	if tableName == "" {
		tableName = r.URL.Query().Get("table")
	}

	// Sanitize table name to prevent SQL injection (must be alphanumeric or underscore)
	for _, char := range tableName {
		if !((char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z') || (char >= '0' && char <= '9') || char == '_') {
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "invalid table name"})
			return
		}
	}

	if tableName == "" {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": "tableName is required"})
		return
	}

	ctx := r.Context()
	query := "SELECT * FROM " + tableName + " LIMIT 100"
	rows, err := h.db.QueryContext(ctx, query)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": fmt.Sprintf("failed to query table %s: %v", tableName, err)})
		return
	}
	defer rows.Close()

	cols, err := rows.Columns()
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	var resultRows []map[string]interface{}
	for rows.Next() {
		columnsData := make([]interface{}, len(cols))
		columnPointers := make([]interface{}, len(cols))
		for i := range columnsData {
			columnPointers[i] = &columnsData[i]
		}

		if err := rows.Scan(columnPointers...); err != nil {
			continue
		}

		rowMap := make(map[string]interface{})
		for i, colName := range cols {
			val := columnsData[i]
			if b, ok := val.([]byte); ok {
				val = string(b)
			}
			if colName == "password_hash" || colName == "password" {
				val = "[REDACTED]"
			}
			rowMap[colName] = val
		}
		resultRows = append(resultRows, rowMap)
	}

	if err := rows.Err(); err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	if err := rows.Close(); err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	var total int64
	if err := h.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM "+tableName).Scan(&total); err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"tableName": tableName,
		"columns":   cols,
		"rows":      resultRows,
		"total":     total,
	})
}
