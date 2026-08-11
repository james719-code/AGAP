package domain

import "time"

type AnalyticsBreakdown struct {
	Label string `json:"label"`
	Count int64  `json:"count"`
}

// TelemetryInsights contains server-calculated operational analytics. Rates
// and averages are nil when the database has no evidence from which to derive
// them; clients must display N/A rather than inventing a zero or percentage.
type TelemetryInsights struct {
	GeneratedAt              time.Time            `json:"generated_at"`
	TotalIncidents           int64                `json:"total_incidents"`
	TotalPackets             int64                `json:"total_packets"`
	TotalDeliveryEvents      int64                `json:"total_delivery_events"`
	MeshPackets              int64                `json:"mesh_packets"`
	DeliveredMeshPackets     int64                `json:"delivered_mesh_packets"`
	MeshDeliveryRate         *float64             `json:"mesh_delivery_rate"`
	AverageRelayHops         *float64             `json:"average_relay_hops"`
	RelayedPackets           int64                `json:"relayed_packets"`
	UniqueRelayDevices       int64                `json:"unique_relay_devices"`
	DuplicateDrops           int64                `json:"duplicate_drops"`
	ActiveResponders         int64                `json:"active_responders"`
	TotalResponders          int64                `json:"total_responders"`
	IncidentTypeDistribution []AnalyticsBreakdown `json:"incident_type_distribution"`
	ChannelDistribution      []AnalyticsBreakdown `json:"channel_distribution"`
	DeliveryPathDistribution []AnalyticsBreakdown `json:"delivery_path_distribution"`
}

type OperationalReport struct {
	GeneratedAt              time.Time            `json:"generated_at"`
	TotalIncidents           int64                `json:"total_incidents"`
	ActiveIncidents          int64                `json:"active_incidents"`
	ResolvedIncidents        int64                `json:"resolved_incidents"`
	FalseAlarms              int64                `json:"false_alarms"`
	FalseAlarmRate           *float64             `json:"false_alarm_rate"`
	AverageAckMinutes        *float64             `json:"average_ack_minutes"`
	AverageDispatchMinutes   *float64             `json:"average_dispatch_minutes"`
	AverageArrivalMinutes    *float64             `json:"average_arrival_minutes"`
	AverageResponseMinutes   *float64             `json:"average_response_minutes"`
	ActiveResponders         int64                `json:"active_responders"`
	TotalResponders          int64                `json:"total_responders"`
	StatusDistribution       []AnalyticsBreakdown `json:"status_distribution"`
	IncidentTypeDistribution []AnalyticsBreakdown `json:"incident_type_distribution"`
	BarangayDistribution     []AnalyticsBreakdown `json:"barangay_distribution"`
	AgencyDispatches         []AnalyticsBreakdown `json:"agency_dispatches"`
	ResponderAssignments     []AnalyticsBreakdown `json:"responder_assignments"`
	UnitDeployments          []AnalyticsBreakdown `json:"unit_deployments"`
}
