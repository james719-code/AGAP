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

type Incident struct {
	ID             int64          `json:"id"`
	PacketID       string         `json:"packet_id"`
	PacketHash     string         `json:"packet_hash"`
	TargetID       TargetID       `json:"target_id"`
	IncidentType   IncidentType   `json:"incident_type"`
	Channel        ChannelType    `json:"channel"`
	DeliveryPath   DeliveryPath   `json:"delivery_path"`
	Latitude       float64        `json:"latitude"`
	Longitude      float64        `json:"longitude"`
	Altitude       float64        `json:"altitude"`
	GPSAccuracy    float64        `json:"gps_accuracy"`
	HopCount       int            `json:"hop_count"`
	TTL            int            `json:"ttl"`
	SenderDeviceID string         `json:"sender_device_id"`
	Status         IncidentStatus `json:"status"`
	EventTimestamp time.Time      `json:"event_timestamp"`
	ReceivedAt     time.Time      `json:"received_at"`
	CreatedAt      time.Time      `json:"created_at"`
	UpdatedAt      time.Time      `json:"updated_at"`
}

type HopEvent struct {
	ID             int64     `json:"id"`
	PacketID       string    `json:"packet_id"`
	HopCount       int       `json:"hop_count"`
	SenderDeviceID string    `json:"sender_device_id"`
	RecordedAt     time.Time `json:"recorded_at"`
}
