package domain

import (
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type Role string

const (
	RoleCitizen    Role = "CITIZEN"
	RoleResponder  Role = "RESPONDER"
	RoleDispatcher Role = "DISPATCHER"
	RoleAdmin      Role = "ADMIN"
)

type User struct {
	ID           int64     `json:"id"`
	Username     string    `json:"username"`
	PasswordHash string    `json:"-"`
	Role         Role      `json:"role"`
	DeviceID     string    `json:"device_id"`
	AgencyID     *int64    `json:"agency_id,omitempty"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
}

type AuthClaims struct {
	UserID   int64  `json:"user_id"`
	Username string `json:"username"`
	Role     Role   `json:"role"`
	DeviceID string `json:"device_id"`
	jwt.RegisteredClaims
}

type LoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
	DeviceID string `json:"device_id,omitempty"`
}

type LoginResponse struct {
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expires_at"`
	User      User      `json:"user"`
}
