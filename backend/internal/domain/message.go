package domain

import "time"

type PredefinedMessageType string

const (
	PredefinedMessageTypeSOS    PredefinedMessageType = "SOS"
	PredefinedMessageTypeAck    PredefinedMessageType = "ACK"
	PredefinedMessageTypeStatus PredefinedMessageType = "STATUS"
)

func (t PredefinedMessageType) IsValid() bool {
	switch t {
	case PredefinedMessageTypeSOS, PredefinedMessageTypeAck, PredefinedMessageTypeStatus:
		return true
	default:
		return false
	}
}

type PredefinedMessage struct {
	ID              int64                 `json:"id"`
	Code            string                `json:"code"`
	ProtocolCode    int                   `json:"protocol_code"`
	MessageEN       string                `json:"message_en"`
	MessageFil      string                `json:"message_fil,omitempty"`
	MessageBikol    string                `json:"message_bikol,omitempty"`
	MessageType     PredefinedMessageType `json:"message_type"`
	EmergencyTypeID *int64                `json:"emergency_type_id,omitempty"`
	TargetRole      string                `json:"target_role,omitempty"`
	IsActive        bool                  `json:"is_active"`
	CreatedAt       time.Time             `json:"created_at"`
	UpdatedAt       time.Time             `json:"updated_at"`
}

type IncidentMessageChannel string

const (
	IncidentMessageChannelOnline IncidentMessageChannel = "ONLINE"
	IncidentMessageChannelBLE    IncidentMessageChannel = "BLE"
)

func (c IncidentMessageChannel) IsValid() bool {
	switch c {
	case IncidentMessageChannelOnline, IncidentMessageChannelBLE:
		return true
	default:
		return false
	}
}

type IncidentMessage struct {
	ID                  int64                  `json:"id"`
	IncidentID          int64                  `json:"incident_id"`
	SenderUserID        *int64                 `json:"sender_user_id,omitempty"`
	ReceiverUserID      *int64                 `json:"receiver_user_id,omitempty"`
	PredefinedMessageID *int64                 `json:"predefined_message_id,omitempty"`
	CustomMessage       string                 `json:"custom_message,omitempty"`
	Channel             IncidentMessageChannel `json:"channel"`
	SentAt              time.Time              `json:"sent_at"`
	ReceivedAt          *time.Time             `json:"received_at,omitempty"`

	PredefinedCode string `json:"predefined_code,omitempty"`
	MessageEN      string `json:"message_en,omitempty"`
	SenderName     string `json:"sender_name,omitempty"`
	ReceiverName   string `json:"receiver_name,omitempty"`
}
