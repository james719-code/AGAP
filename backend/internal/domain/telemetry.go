package domain

import "time"

type IncidentType string

const (
	IncidentTypeCrime    IncidentType = "CRIME"
	IncidentTypeMedical  IncidentType = "MEDICAL"
	IncidentTypeAccident IncidentType = "ACCIDENT"
	IncidentTypeDisaster IncidentType = "DISASTER"
	IncidentTypeFire     IncidentType = "FIRE"
)

func (t IncidentType) DefaultEmergencyTypeID() int {
	switch t {
	case IncidentTypeMedical:
		return 1
	case IncidentTypeAccident:
		return 2
	case IncidentTypeFire:
		return 3
	case IncidentTypeDisaster:
		return 4
	case IncidentTypeCrime:
		return 5
	default:
		return 0
	}
}

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

type MeshPacketType string

const (
	MeshPacketTypeSOS    MeshPacketType = "SOS"
	MeshPacketTypeAck    MeshPacketType = "ACK"
	MeshPacketTypeStatus MeshPacketType = "STATUS"
)

type MeshDeliveryEventType string

const (
	MeshEventCreated          MeshDeliveryEventType = "CREATED"
	MeshEventReceived         MeshDeliveryEventType = "RECEIVED"
	MeshEventRelayed          MeshDeliveryEventType = "RELAYED"
	MeshEventTargetReached    MeshDeliveryEventType = "TARGET_REACHED"
	MeshEventUplinked         MeshDeliveryEventType = "UPLINKED"
	MeshEventDuplicateDropped MeshDeliveryEventType = "DUPLICATE_DROPPED"
)

type TelemetryPacket struct {
	IncidentUUID   string       `json:"incident_uuid,omitempty"`
	PacketUUID     string       `json:"packet_uuid"`
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
	StatusCreated      IncidentStatus = "CREATED"
	StatusQueued       IncidentStatus = "QUEUED"
	StatusReceived     IncidentStatus = "RECEIVED"
	StatusAcknowledged IncidentStatus = "ACKNOWLEDGED"
	StatusDispatched   IncidentStatus = "DISPATCHED"
	StatusEnRoute      IncidentStatus = "EN_ROUTE"
	StatusOnScene      IncidentStatus = "ON_SCENE"
	StatusResolved     IncidentStatus = "RESOLVED"
	StatusCancelled    IncidentStatus = "CANCELLED"
	StatusFalseAlarm   IncidentStatus = "FALSE_ALARM"
)

type IncidentStatusHistory struct {
	ID         int64          `json:"id"`
	IncidentID int64          `json:"incident_id"`
	Status     IncidentStatus `json:"status"`
	ChangedBy  *int64         `json:"changed_by,omitempty"`
	Latitude   *float64       `json:"latitude,omitempty"`
	Longitude  *float64       `json:"longitude,omitempty"`
	Notes      string         `json:"notes,omitempty"`
	CreatedAt  time.Time      `json:"created_at"`
}

type EmergencyType struct {
	ID               int64     `json:"id"`
	Code             string    `json:"code"`
	Name             string    `json:"name"`
	Description      string    `json:"description,omitempty"`
	DefaultPriority  int       `json:"default_priority"`
	ProtocolCode     int       `json:"protocol_code"`
	TargetAgencyType string    `json:"target_agency_type,omitempty"`
	IsActive         bool      `json:"is_active"`
	CreatedAt        time.Time `json:"created_at"`
	UpdatedAt        time.Time `json:"updated_at"`
}

type Incident struct {
	ID               int64          `json:"id"`
	IncidentUUID     string         `json:"incident_uuid"`
	PacketUUID       string         `json:"packet_uuid,omitempty"`
	PacketHash       string         `json:"packet_hash"`
	CitizenID        *int64         `json:"citizen_id,omitempty"`
	ResponderID      *int64         `json:"responder_id,omitempty"`
	DispatchedUnitID *int64         `json:"dispatched_unit_id,omitempty"`
	EmergencyTypeID  *int64         `json:"emergency_type_id,omitempty"`
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
	PacketUUID     string    `json:"packet_uuid"`
	HopCount       int       `json:"hop_count"`
	SenderDeviceID string    `json:"sender_device_id"`
	RelayLat       float64   `json:"relay_lat"`
	RelayLon       float64   `json:"relay_lon"`
	RecordedAt     time.Time `json:"recorded_at"`
}

type MeshDeliveryEvent struct {
	PacketUUID       string                `json:"packet_uuid"`
	PacketType       MeshPacketType        `json:"packet_type"`
	IncidentID       *int64                `json:"incident_id,omitempty"`
	OriginDeviceID   string                `json:"origin_device_identifier,omitempty"`
	PayloadHash      string                `json:"payload_hash,omitempty"`
	EventType        MeshDeliveryEventType `json:"event_type"`
	DeviceIdentifier string                `json:"device_identifier,omitempty"`
	Latitude         *float64              `json:"latitude,omitempty"`
	Longitude        *float64              `json:"longitude,omitempty"`
	RecordedAt       time.Time             `json:"recorded_at"`
	SyncedAt         *time.Time            `json:"synced_at,omitempty"`
}
