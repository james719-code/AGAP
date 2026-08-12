package domain

import "time"

type AlertCategory string

const (
	AlertCategoryWeather        AlertCategory = "WEATHER"
	AlertCategoryEvacuation     AlertCategory = "EVACUATION"
	AlertCategorySafety         AlertCategory = "SAFETY"
	AlertCategoryHealth         AlertCategory = "HEALTH"
	AlertCategoryInfrastructure AlertCategory = "INFRASTRUCTURE"
	AlertCategoryGeneral        AlertCategory = "GENERAL"
)

func (c AlertCategory) IsValid() bool {
	switch c {
	case AlertCategoryWeather, AlertCategoryEvacuation, AlertCategorySafety,
		AlertCategoryHealth, AlertCategoryInfrastructure, AlertCategoryGeneral:
		return true
	default:
		return false
	}
}

type AlertSeverity string

const (
	AlertSeverityInfo      AlertSeverity = "INFO"
	AlertSeverityWarning   AlertSeverity = "WARNING"
	AlertSeverityCritical  AlertSeverity = "CRITICAL"
	AlertSeverityEmergency AlertSeverity = "EMERGENCY"
)

func (s AlertSeverity) IsValid() bool {
	switch s {
	case AlertSeverityInfo, AlertSeverityWarning, AlertSeverityCritical, AlertSeverityEmergency:
		return true
	default:
		return false
	}
}

type AdminAlert struct {
	ID                  int64         `json:"id"`
	UUID                string        `json:"uuid"`
	AuthorID            int64         `json:"author_id"`
	AuthorName          string        `json:"author_name,omitempty"`
	PredefinedMessageID *int64        `json:"predefined_message_id,omitempty"`
	Title               string        `json:"title"`
	Body                string        `json:"body"`
	Category            AlertCategory `json:"category"`
	Severity            AlertSeverity `json:"severity"`
	TargetRegionCode    string        `json:"target_region_code,omitempty"`
	TargetProvinceCode  string        `json:"target_province_code,omitempty"`
	TargetMuniCode      string        `json:"target_muni_code,omitempty"`
	TargetBarangayCode  string        `json:"target_barangay_code,omitempty"`
	IsPinned            bool          `json:"is_pinned"`
	IsActive            bool          `json:"is_active"`
	PublishedAt         time.Time     `json:"published_at"`
	ExpiresAt           *time.Time    `json:"expires_at,omitempty"`
	CreatedAt           time.Time     `json:"created_at"`
	UpdatedAt           time.Time     `json:"updated_at"`
	ReadCount           int           `json:"read_count"`
	IsRead              bool          `json:"is_read,omitempty"`
}

type AdminAlertRead struct {
	ID      int64     `json:"id"`
	AlertID int64     `json:"alert_id"`
	UserID  int64     `json:"user_id"`
	ReadAt  time.Time `json:"read_at"`
}
