package domain

import "time"

type IncidentType string

const (
	IncidentTypeCrime    IncidentType = "CRIME"
	IncidentTypeMedical  IncidentType = "MEDICAL"
	IncidentTypeAccident IncidentType = "ACCIDENT"
	IncidentTypeDisaster IncidentType = "DISASTER"
)

type ChannelType string

const (
	ChannelOnline  ChannelType = "ONLINE"
	ChannelBLEMesh ChannelType = "BLE_MESH"
)

type DeliveryPath string

const (
	DeliveryDirectUplink  DeliveryPath = "DIRECT_UPLINK"
	DeliveryCloudShortcut DeliveryPath = "BLE_CLOUD_SHORTCUT"
)

type TelemetryPacket struct {
	PacketID       string       `json:"packet_id"`
	PacketHash     string       `json:"packet_hash,omitempty"`
	HMACSignature  string       `json:"hmac_signature,omitempty"`
	TargetID       TargetID     `json:"target_id"`
	IncidentType   IncidentType `json:"incident_type"`
	Channel        ChannelType  `json:"channel"`
	DeliveryPath   DeliveryPath `json:"delivery_path"`
	Latitude       float64      `json:"latitude"`
	Longitude      float64      `json:"longitude"`
	Altitude       float64      `json:"altitude"`
	GPSAccuracy    float64      `json:"gps_accuracy"`
	HopCount       int          `json:"hop_count"`
	TTL            int          `json:"ttl"`
	AESPayload     string       `json:"aes_payload,omitempty"`
	SenderDeviceID string       `json:"sender_device_id"`
	Timestamp      time.Time    `json:"timestamp"`
	ReceivedAt     time.Time    `json:"received_at"`
}

type IncidentStatus string

const (
	StatusActive     IncidentStatus = "ACTIVE"
	StatusDispatched IncidentStatus = "DISPATCHED"
	StatusResolved   IncidentStatus = "RESOLVED"
)

type AccidentType struct {
	ID                          int64     `json:"id"`
	Codename                    string    `json:"codename"`
	Name                        string    `json:"name"`
	PossibleMessagesDescription string    `json:"possible_messages_description"`
	CreatedAt                   time.Time `json:"created_at"`
}

type Incident struct {
	ID               int64          `json:"id"`
	PacketID         string         `json:"packet_id"`
	PacketHash       string         `json:"packet_hash"`
	CitizenID        *int64         `json:"citizen_id,omitempty"`
	ResponderID      *int64         `json:"responder_id,omitempty"`
	DispatchedUnitID *int64         `json:"dispatched_unit_id,omitempty"`
	AccidentTypeID   *int64         `json:"accident_type_id,omitempty"`
	AddressID        *int64         `json:"address_id,omitempty"`
	TargetID         TargetID       `json:"target_id"`
	IncidentType     IncidentType   `json:"incident_type"`
	Channel          ChannelType    `json:"channel"`
	DeliveryPath     DeliveryPath   `json:"delivery_path"`
	Latitude         float64        `json:"latitude"`
	Longitude        float64        `json:"longitude"`
	Altitude         float64        `json:"altitude"`
	GPSAccuracy      float64        `json:"gps_accuracy"`
	HopCount         int            `json:"hop_count"`
	TTL              int            `json:"ttl"`
	SenderDeviceID   string         `json:"sender_device_id"`
	FalsePositive    bool           `json:"false_positive"`
	Status           IncidentStatus `json:"status"`
	Description      string         `json:"description"`
	DispatcherNotes  string         `json:"dispatcher_notes"`
	EventTimestamp   time.Time      `json:"event_timestamp"`
	ReceivedAt       time.Time      `json:"received_at"`
	DispatchedAt     *time.Time     `json:"dispatched_at,omitempty"`
	ResolvedAt       *time.Time     `json:"resolved_at,omitempty"`
	CreatedAt        time.Time      `json:"created_at"`
	UpdatedAt        time.Time      `json:"updated_at"`
}

type HopEvent struct {
	ID             int64     `json:"id"`
	PacketID       string    `json:"packet_id"`
	HopCount       int       `json:"hop_count"`
	SenderDeviceID string    `json:"sender_device_id"`
	RelayLat       float64   `json:"relay_lat"`
	RelayLon       float64   `json:"relay_lon"`
	RecordedAt     time.Time `json:"recorded_at"`
}



