package domain

import "time"

type LocationSourceType string

const (
	LocationSourceGPS               LocationSourceType = "GPS"
	LocationSourceMapPin            LocationSourceType = "MAP_PIN"
	LocationSourceBarangayReference LocationSourceType = "BARANGAY_REFERENCE"
	LocationSourceUnknown           LocationSourceType = "UNKNOWN"
)

type IncidentReport struct {
	ID                   int64              `json:"id"`
	ReportUUID           string             `json:"report_uuid"`
	TransportToken       string             `json:"transport_token"`
	IncidentID           *int64             `json:"incident_id,omitempty"`
	ReporterUserID       *int64             `json:"reporter_user_id,omitempty"`
	ReporterDeviceID     string             `json:"reporter_device_id,omitempty"`
	GatewayDeviceID      string             `json:"gateway_device_id,omitempty"`
	SubmissionType       string             `json:"submission_type"`
	SubjectRelation      string             `json:"subject_relation"`
	EmergencyType        string             `json:"emergency_type"`
	Subtype              string             `json:"subtype"`
	Description          string             `json:"description"`
	PeopleAffected       int                `json:"people_affected"`
	ImmediateDanger      bool               `json:"immediate_danger"`
	LocationSource       LocationSourceType `json:"location_source"`
	SelectedBarangayCode string             `json:"selected_barangay_code,omitempty"`
	Latitude             *float64           `json:"latitude,omitempty"`
	Longitude            *float64           `json:"longitude,omitempty"`
	GPSAccuracyMeters    *float64           `json:"gps_accuracy_meters,omitempty"`
	ObservedAt           time.Time          `json:"observed_at"`
	OriginChannel        string             `json:"origin_channel"`
	DetailState          string             `json:"detail_state"` // MINIMAL or FULL
	CreatedAt            time.Time          `json:"created_at"`
}

type CreateReportRequest struct {
	ReportUUID           string             `json:"report_uuid"`
	TransportToken       string             `json:"transport_token"`
	SubmissionType       string             `json:"submission_type"`
	SubjectRelation      string             `json:"subject_relation"`
	EmergencyType        string             `json:"emergency_type"`
	Subtype              string             `json:"subtype"`
	Description          string             `json:"description"`
	PeopleAffected       int                `json:"people_affected"`
	ImmediateDanger      bool               `json:"immediate_danger"`
	LocationSource       LocationSourceType `json:"location_source"`
	SelectedBarangayCode string             `json:"selected_barangay_code"`
	Latitude             *float64           `json:"latitude"`
	Longitude            *float64           `json:"longitude"`
	GPSAccuracyMeters    *float64           `json:"gps_accuracy_meters"`
	GatewayDeviceID      string             `json:"gateway_device_id"`
	OriginChannel        string             `json:"origin_channel"`
}
