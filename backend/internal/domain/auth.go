package domain

import (
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type Role string

const (
	RoleCitizen   Role = "CITIZEN"
	RoleResponder Role = "RESPONDER"
	RoleAdmin     Role = "ADMIN"
)

type AccountStatus string

const (
	AccountStatusPending   AccountStatus = "PENDING"
	AccountStatusApproved  AccountStatus = "APPROVED"
	AccountStatusRejected  AccountStatus = "REJECTED"
	AccountStatusSuspended AccountStatus = "SUSPENDED"
)

type UserDevice struct {
	ID               int64     `json:"id"`
	UserID           int64     `json:"user_id"`
	DeviceHardwareID string    `json:"device_hardware_id"`
	DeviceName       string    `json:"device_name"`
	Platform         string    `json:"platform"`
	LastSeenAt       time.Time `json:"last_seen_at"`
	CreatedAt        time.Time `json:"created_at"`
}

type EmergencyContact struct {
	ID           int64     `json:"id"`
	UserID       int64     `json:"user_id"`
	ContactName  string    `json:"contact_name"`
	PhoneNumber  string    `json:"phone_number"`
	Relationship string    `json:"relationship"`
	IsPrimary    bool      `json:"is_primary"`
	CreatedAt    time.Time `json:"created_at"`
}

type User struct {
	ID                int64              `json:"id"`
	Username          string             `json:"username"`
	PasswordHash      string             `json:"-"`
	Role              Role               `json:"role"`
	DeviceID          string             `json:"device_id"`
	AgencyID          *int64             `json:"agency_id,omitempty"`
	RescueUnitID      *int64             `json:"rescue_unit_id,omitempty"`
	AddressID         *int64             `json:"address_id,omitempty"`
	Name              string             `json:"name"`
	Birthday          string             `json:"birthday"`
	Gender            string             `json:"gender"`
	PhoneNumber       string             `json:"phone_number"`
	Email             string             `json:"email"`
	MedicalInfo       string             `json:"medical_info"`
	NationalID        string             `json:"national_id"`
	AccountStatus     AccountStatus      `json:"account_status"`
	IsVerified        bool               `json:"is_verified"`
	ImageURL          string             `json:"image_url"`
	LastLoginAt       *time.Time         `json:"last_login_at,omitempty"`
	Devices           []UserDevice       `json:"devices,omitempty"`
	EmergencyContacts []EmergencyContact `json:"emergency_contacts,omitempty"`
	CreatedAt         time.Time          `json:"created_at"`
	UpdatedAt         time.Time          `json:"updated_at"`
}

type RefreshToken struct {
	ID        int64     `json:"id"`
	UserID    int64     `json:"user_id"`
	TokenHash string    `json:"token_hash"`
	ExpiresAt time.Time `json:"expires_at"`
	Revoked   bool      `json:"revoked"`
	CreatedAt time.Time `json:"created_at"`
}

type Notification struct {
	ID         int64      `json:"id"`
	UserID     int64      `json:"user_id"`
	IncidentID *int64     `json:"incident_id,omitempty"`
	Type       string     `json:"type"`
	Payload    string     `json:"payload"`
	IsRead     bool       `json:"is_read"`
	SentAt     time.Time  `json:"sent_at"`
	ReadAt     *time.Time `json:"read_at,omitempty"`
}

type AuditLog struct {
	ID         int64     `json:"id"`
	ActorID    *int64    `json:"actor_id,omitempty"`
	Action     string    `json:"action"`
	EntityType string    `json:"entity_type"`
	EntityID   int64     `json:"entity_id"`
	OldValue   string    `json:"old_value,omitempty"`
	NewValue   string    `json:"new_value,omitempty"`
	CreatedAt  time.Time `json:"created_at"`
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
	Token        string    `json:"token"`
	RefreshToken string    `json:"refresh_token,omitempty"`
	ExpiresAt    time.Time `json:"expires_at"`
	User         User      `json:"user"`
}

type RefreshTokenRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type UpdateProfileRequest struct {
	Name        string `json:"name,omitempty"`
	Birthday    string `json:"birthday,omitempty"`
	Gender      string `json:"gender,omitempty"`
	PhoneNumber string `json:"phone_number,omitempty"`
	Email       string `json:"email,omitempty"`
	MedicalInfo string `json:"medical_info,omitempty"`
	NationalID  string `json:"national_id,omitempty"`
	ImageURL    string `json:"image_url,omitempty"`
	Role        *Role  `json:"role,omitempty"`
	AgencyID    *int64 `json:"agency_id,omitempty"`
}

type EmergencyContactRequest struct {
	ContactName  string `json:"contact_name"`
	PhoneNumber  string `json:"phone_number"`
	Relationship string `json:"relationship,omitempty"`
	IsPrimary    *bool  `json:"is_primary,omitempty"`
}
