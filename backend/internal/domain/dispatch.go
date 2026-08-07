package domain

import (
	"time"
)

type DispatchStatus string

const (
	DispatchStatusAssigned  DispatchStatus = "ASSIGNED"
	DispatchStatusAccepted  DispatchStatus = "ACCEPTED"
	DispatchStatusEnRoute   DispatchStatus = "EN_ROUTE"
	DispatchStatusOnScene   DispatchStatus = "ON_SCENE"
	DispatchStatusCompleted DispatchStatus = "COMPLETED"
	DispatchStatusCancelled DispatchStatus = "CANCELLED"
)

type IncidentDispatch struct {
	ID           int64          `json:"id"`
	IncidentID   int64          `json:"incident_id"`
	UnitID       int64          `json:"unit_id"`
	DispatchedBy *int64         `json:"dispatched_by,omitempty"`
	Status       DispatchStatus `json:"status"`
	DispatchedAt time.Time      `json:"dispatched_at"`
	AcceptedAt   *time.Time     `json:"accepted_at,omitempty"`
	DepartedAt   *time.Time     `json:"departed_at,omitempty"`
	ArrivedAt    *time.Time     `json:"arrived_at,omitempty"`
	CompletedAt  *time.Time     `json:"completed_at,omitempty"`

	// Joined metadata
	UnitCallsign  string `json:"unit_callsign,omitempty"`
	AgencyName    string `json:"agency_name,omitempty"`
	ResponderName string `json:"responder_name,omitempty"`
}

type CreateDispatchRequest struct {
	UnitID int64 `json:"unit_id"`
}

type UpdateDispatchStatusRequest struct {
	Status DispatchStatus `json:"status"`
}
