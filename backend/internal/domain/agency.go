package domain

import "time"

type AgencyType string

const (
	AgencyDRRMO    AgencyType = "DRRMO"
	AgencyPolice   AgencyType = "POLICE"
	AgencyFire     AgencyType = "FIRE"
	AgencyMedical  AgencyType = "MEDICAL"
	AgencyVolutary AgencyType = "VOLUNTEER"
)

type Agency struct {
	ID           int64      `json:"id"`
	Code         string     `json:"code"`         // Unique agency code e.g. "DRRMO-LEGAZPI"
	Name         string     `json:"name"`         // Human readable name
	Type         AgencyType `json:"type"`         // DRRMO, POLICE, etc.
	RegionCode   string     `json:"region_code"`  // "05"
	ProvinceCode string     `json:"province_code"`// "05"
	MuniCode     string     `json:"muni_code"`    // "12"
	BarangayCode string     `json:"barangay_code"`// "01"
	ContactNumber string    `json:"contact_number"`
	Latitude     float64    `json:"latitude"`
	Longitude    float64    `json:"longitude"`
	CreatedAt    time.Time  `json:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at"`
}

type ResponderUnit struct {
	ID            int64     `json:"id"`
	AgencyID      int64     `json:"agency_id"`
	TargetID      string    `json:"target_id"`       // Full spatial target ID e.g. 05-05-12-01-04::UUID
	UnitCallsign  string    `json:"unit_callsign"`   // "DRRMO-ALPHA-1"
	DeviceID      string    `json:"device_id"`       // Responder mobile device UUID
	Status        string    `json:"status"`          // "IDLE" | "DISPATCHED" | "OFFLINE"
	CurrentLat    float64   `json:"current_lat"`
	CurrentLon    float64   `json:"current_lon"`
	LastSeenAt    time.Time `json:"last_seen_at"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}
