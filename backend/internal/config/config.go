package config

import (
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

type Config struct {
	Port               string
	Env                string
	DBType             string
	DBFilePath         string
	DBDsn              string
	JWTSecret          string
	AESSecretKey       string
	CORSAllowedOrigins []string
	DedupTTLSeconds    int
	RateLimitRPS       float64
	RateLimitBurst     int
}

func Load() (*Config, error) {
	// Try to load .env file if it exists, ignore error if missing
	_ = godotenv.Load(".env")
	_ = godotenv.Load("../.env")

	port := getEnv("PORT", "8080")
	env := getEnv("ENV", "development")
	dbType := getEnv("DB_TYPE", "sqlite")
	dbFilePath := getEnv("DB_FILE_PATH", "./agap_gis.db")
	dbDsn := getEnv("DB_DSN", "")
	jwtSecret := getEnv("JWT_SECRET", "agap_backend_jwt_secret_key_2026")
	aesSecretKey := getEnv("AES_SECRET_KEY", "0123456789abcdef0123456789abcdef")

	corsOriginsStr := getEnv("CORS_ALLOWED_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173")
	corsOrigins := strings.Split(corsOriginsStr, ",")
	for i := range corsOrigins {
		corsOrigins[i] = strings.TrimSpace(corsOrigins[i])
	}

	dedupTTLSeconds, _ := strconv.Atoi(getEnv("DEDUP_TTL_SECONDS", "300"))
	if dedupTTLSeconds <= 0 {
		dedupTTLSeconds = 300
	}

	rps, _ := strconv.ParseFloat(getEnv("RATE_LIMIT_RPS", "20"), 64)
	if rps <= 0 {
		rps = 20
	}

	burst, _ := strconv.Atoi(getEnv("RATE_LIMIT_BURST", "40"))
	if burst <= 0 {
		burst = 40
	}

	return &Config{
		Port:               port,
		Env:                env,
		DBType:             dbType,
		DBFilePath:         dbFilePath,
		DBDsn:              dbDsn,
		JWTSecret:          jwtSecret,
		AESSecretKey:       aesSecretKey,
		CORSAllowedOrigins: corsOrigins,
		DedupTTLSeconds:    dedupTTLSeconds,
		RateLimitRPS:       rps,
		RateLimitBurst:     burst,
	}, nil
}

func (c *Config) DedupTTL() time.Duration {
	return time.Duration(c.DedupTTLSeconds) * time.Second
}

func getEnv(key, fallback string) string {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		return val
	}
	return fallback
}
