package handler

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"runtime"
	"strconv"
	"strings"
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

type HardwareInfo struct {
	CPUs                  int      `json:"cpus"`
	Goroutines            int      `json:"goroutines"`
	MemoryAllocMB         float64  `json:"memory_alloc_mb"`
	MemorySysMB           float64  `json:"memory_sys_mb"`
	TemperatureCelsius    *float64 `json:"temperature_celsius,omitempty"`
	ThermalStatus         string   `json:"thermal_status"` // "NORMAL" | "WARNING_HIGH_TEMP" | "CRITICAL_OVERHEAT" | "UNAVAILABLE"
	DeviceUptimeFormatted string   `json:"device_uptime_formatted,omitempty"`
	DeviceUptimeSeconds   int64    `json:"device_uptime_seconds,omitempty"`
}

type SystemInfoResponse struct {
	Status          string       `json:"status"`
	UptimeSeconds   int64        `json:"uptime_seconds"`
	UptimeFormatted string       `json:"uptime_formatted"`
	API             APIInfo      `json:"api"`
	Database        DatabaseInfo `json:"database"`
	Hardware        HardwareInfo `json:"hardware"`
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

	hwInfo := getHardwareInfo()
	if hwInfo.ThermalStatus == "CRITICAL_OVERHEAT" && overallStatus == "ok" {
		overallStatus = "critical_thermal_warning"
	}

	uptimeDuration := time.Since(startTime)
	uptimeSecs := int64(uptimeDuration.Seconds())
	uptimeFormatted := formatDuration(uptimeDuration)

	resp := SystemInfoResponse{
		Status:          overallStatus,
		UptimeSeconds:   uptimeSecs,
		UptimeFormatted: uptimeFormatted,
		API: APIInfo{
			Name:              "AGAP Telemetry & Dispatch Backend Engine",
			Version:           "1.0.0",
			Environment:       envStr,
			ServerTime:        time.Now().UTC().Format(time.RFC3339),
			Uptime:            uptimeFormatted,
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
		Hardware: hwInfo,
	}

	w.Header().Set("Content-Type", "application/json")
	if overallStatus != "ok" {
		w.WriteHeader(http.StatusMultiStatus)
	}
	_ = json.NewEncoder(w).Encode(resp)
}

func getHardwareInfo() HardwareInfo {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	temp, status := readServerTemperature()
	devUptimeStr, devUptimeSecs := readHostDeviceUptime()

	return HardwareInfo{
		CPUs:                  runtime.NumCPU(),
		Goroutines:            runtime.NumGoroutine(),
		MemoryAllocMB:         float64(m.Alloc) / (1024 * 1024),
		MemorySysMB:           float64(m.Sys) / (1024 * 1024),
		TemperatureCelsius:    temp,
		ThermalStatus:         status,
		DeviceUptimeFormatted: devUptimeStr,
		DeviceUptimeSeconds:   devUptimeSecs,
	}
}

func readServerTemperature() (*float64, string) {
	paths := []string{
		"/sys/class/thermal/thermal_zone0/temp",
		"/sys/class/thermal/thermal_zone1/temp",
		"/sys/class/hwmon/hwmon0/temp1_input",
		"/sys/class/hwmon/hwmon1/temp1_input",
	}

	for _, p := range paths {
		bytes, err := os.ReadFile(p)
		if err == nil {
			rawStr := strings.TrimSpace(string(bytes))
			if val, err := strconv.ParseFloat(rawStr, 64); err == nil {
				tempC := val
				if tempC > 1000 {
					tempC = tempC / 1000.0
				}
				if tempC > 0 && tempC < 150 {
					status := "NORMAL"
					if tempC > 80.0 {
						status = "CRITICAL_OVERHEAT"
					} else if tempC >= 65.0 {
						status = "WARNING_HIGH_TEMP"
					}
					return &tempC, status
				}
			}
		}
	}

	return nil, "UNAVAILABLE"
}

func readHostDeviceUptime() (string, int64) {
	bytes, err := os.ReadFile("/proc/uptime")
	if err == nil {
		fields := strings.Fields(string(bytes))
		if len(fields) > 0 {
			if secs, err := strconv.ParseFloat(fields[0], 64); err == nil && secs > 0 {
				dur := time.Duration(secs * float64(time.Second))
				return formatDuration(dur), int64(secs)
			}
		}
	}
	return "", 0
}

func formatDuration(d time.Duration) string {
	d = d.Truncate(time.Second)
	days := int(d.Hours()) / 24
	hours := int(d.Hours()) % 24
	minutes := int(d.Minutes()) % 60
	seconds := int(d.Seconds()) % 60

	if days > 0 {
		return fmt.Sprintf("%dd %dh %dm", days, hours, minutes)
	}
	if hours > 0 {
		return fmt.Sprintf("%dh %dm %ds", hours, minutes, seconds)
	}
	if minutes > 0 {
		return fmt.Sprintf("%dm %ds", minutes, seconds)
	}
	return fmt.Sprintf("%ds", seconds)
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
