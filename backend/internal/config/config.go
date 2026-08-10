package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

const (
	defaultJWTSecret    = "agap_backend_jwt_secret_key_2026"
	defaultAESSecretKey = "0123456789abcdef0123456789abcdef"
)

type Config struct {
	Host                 string
	Port                 string
	Env                  string
	DBType               string
	DBFilePath           string
	DBDsn                string
	JWTSecret            string
	AESSecretKey         string
	CORSAllowedOrigins   []string
	DedupTTLSeconds      int
	RateLimitRPS         float64
	RateLimitBurst       int
	StaticDir            string
	EnableSystemExplorer bool
	RequireTelemetryHMAC bool
	MaxRequestBodyBytes  int64
	RoutingGraphPath     string
	AutoCleanupEnabled          bool
	CleanupIntervalMinutes      int
	MeshEventsRetentionDays     int
	MeshPacketsRetentionDays    int
	RefreshTokensRetentionDays int
	NotificationsRetentionDays  int
	AuditLogsRetentionDays      int
}

func Load() (*Config, error) {
	// Try to load .env files if present. Environment variables still take precedence.
	_ = godotenv.Load(".env")
	_ = godotenv.Load("../.env")

	host := getEnv("HOST", "0.0.0.0")
	port := getEnv("PORT", "8080")
	env := strings.ToLower(strings.TrimSpace(getEnv("ENV", "development")))
	dbType := strings.ToLower(strings.TrimSpace(getEnv("DB_TYPE", "sqlite")))
	dbFilePath := getEnv("DB_FILE_PATH", "./agap_gis.db")
	dbDsn := getEnv("DB_DSN", "")
	jwtSecret := getEnv("JWT_SECRET", defaultJWTSecret)
	aesSecretKey := getEnv("AES_SECRET_KEY", defaultAESSecretKey)

	corsOriginsStr := getEnv("CORS_ALLOWED_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173")
	corsOrigins := splitNonEmpty(corsOriginsStr)

	dedupTTLSeconds := getPositiveInt("DEDUP_TTL_SECONDS", 300)
	rps := getPositiveFloat("RATE_LIMIT_RPS", 20)
	burst := getPositiveInt("RATE_LIMIT_BURST", 40)
	maxRequestBodyBytes := int64(getPositiveInt("MAX_REQUEST_BODY_BYTES", 2*1024*1024))

	enableSystemExplorer := getBoolEnv("ENABLE_SYSTEM_EXPLORER", env != "production")
	requireTelemetryHMAC := getBoolEnv("REQUIRE_TELEMETRY_HMAC", false)

	routingGraphPath := getEnv("ROUTING_GRAPH_PATH", "/opt/agap/maps/deployment-routing.db")

	autoCleanupEnabled := getBoolEnv("AUTO_CLEANUP_ENABLED", true)
	cleanupIntervalMinutes := getPositiveInt("CLEANUP_INTERVAL_MINUTES", 60)
	meshEventsRetentionDays := getPositiveInt("RETENTION_MESH_EVENTS_DAYS", 7)
	meshPacketsRetentionDays := getPositiveInt("RETENTION_MESH_PACKETS_DAYS", 14)
	refreshTokensRetentionDays := getPositiveInt("RETENTION_REFRESH_TOKENS_DAYS", 7)
	notificationsRetentionDays := getPositiveInt("RETENTION_NOTIFICATIONS_DAYS", 30)
	auditLogsRetentionDays := getPositiveInt("RETENTION_AUDIT_LOGS_DAYS", 90)

	staticDir := getEnv("STATIC_DIR", "")
	if staticDir == "" {
		candidates := []string{
			"/opt/agap/static",
			"/opt/agap/frontend",
			"../frontend/dist",
			"./frontend/dist",
		}
		for _, dir := range candidates {
			if _, err := os.Stat(dir + "/index.html"); err == nil {
				staticDir = dir
				break
			}
		}
	}

	cfg := &Config{
		Host:                        host,
		Port:                        port,
		Env:                         env,
		DBType:                      dbType,
		DBFilePath:                  dbFilePath,
		DBDsn:                       dbDsn,
		JWTSecret:                   jwtSecret,
		AESSecretKey:                aesSecretKey,
		CORSAllowedOrigins:          corsOrigins,
		DedupTTLSeconds:             dedupTTLSeconds,
		RateLimitRPS:                rps,
		RateLimitBurst:              burst,
		StaticDir:                   staticDir,
		EnableSystemExplorer:        enableSystemExplorer,
		RequireTelemetryHMAC:        requireTelemetryHMAC,
		MaxRequestBodyBytes:         maxRequestBodyBytes,
		RoutingGraphPath:            routingGraphPath,
		AutoCleanupEnabled:          autoCleanupEnabled,
		CleanupIntervalMinutes:      cleanupIntervalMinutes,
		MeshEventsRetentionDays:     meshEventsRetentionDays,
		MeshPacketsRetentionDays:    meshPacketsRetentionDays,
		RefreshTokensRetentionDays: refreshTokensRetentionDays,
		NotificationsRetentionDays:  notificationsRetentionDays,
		AuditLogsRetentionDays:      auditLogsRetentionDays,
	}


	if err := cfg.Validate(); err != nil {
		return nil, err
	}
	return cfg, nil
}

func (c *Config) Validate() error {
	if c.DBType == "sqlite" && strings.TrimSpace(c.DBFilePath) == "" {
		return fmt.Errorf("DB_FILE_PATH is required when DB_TYPE=sqlite")
	}
	if len(c.JWTSecret) < 24 {
		return fmt.Errorf("JWT_SECRET must be at least 24 characters")
	}
	if len(c.AESSecretKey) < 32 {
		return fmt.Errorf("AES_SECRET_KEY must be at least 32 characters")
	}

	if c.Env == "production" {
		if c.JWTSecret == defaultJWTSecret {
			return fmt.Errorf("JWT_SECRET must be changed before running with ENV=production")
		}
		if c.AESSecretKey == defaultAESSecretKey {
			return fmt.Errorf("AES_SECRET_KEY must be changed before running with ENV=production")
		}
		for _, origin := range c.CORSAllowedOrigins {
			if origin == "*" {
				return fmt.Errorf("CORS_ALLOWED_ORIGINS cannot contain '*' in production")
			}
		}
	}
	return nil
}

func (c *Config) DedupTTL() time.Duration {
	return time.Duration(c.DedupTTLSeconds) * time.Second
}

func getEnv(key, fallback string) string {
	if val, ok := os.LookupEnv(key); ok && strings.TrimSpace(val) != "" {
		return strings.TrimSpace(val)
	}
	return fallback
}

func getPositiveInt(key string, fallback int) int {
	value, err := strconv.Atoi(getEnv(key, strconv.Itoa(fallback)))
	if err != nil || value <= 0 {
		return fallback
	}
	return value
}

func getPositiveFloat(key string, fallback float64) float64 {
	value, err := strconv.ParseFloat(getEnv(key, strconv.FormatFloat(fallback, 'f', -1, 64)), 64)
	if err != nil || value <= 0 {
		return fallback
	}
	return value
}

func getBoolEnv(key string, fallback bool) bool {
	value, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(value) == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(strings.TrimSpace(value))
	if err != nil {
		return fallback
	}
	return parsed
}

func splitNonEmpty(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part != "" {
			result = append(result, part)
		}
	}
	return result
}
