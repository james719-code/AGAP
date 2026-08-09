export type IncidentType = 'CRIME' | 'MEDICAL' | 'ACCIDENT' | 'DISASTER' | 'FIRE';
export type ChannelType = 'ONLINE' | 'BLE_MESH';
export type DeliveryPath = 'DIRECT_UPLINK' | 'BLE_CLOUD_SHORTCUT';
export type IncidentStatus = 'CREATED' | 'QUEUED' | 'RECEIVED' | 'ACKNOWLEDGED' | 'DISPATCHED' | 'EN_ROUTE' | 'ON_SCENE' | 'RESOLVED' | 'CANCELLED' | 'FALSE_ALARM';
export type DispatchStatus = 'ASSIGNED' | 'ACCEPTED' | 'EN_ROUTE' | 'ON_SCENE' | 'COMPLETED' | 'CANCELLED';
export type AgencyType = 'RESCUE' | 'DRRMO' | 'POLICE' | 'FIRE' | 'MEDICAL' | 'VOLUNTEER';
export type ResponderStatus = 'IDLE' | 'DISPATCHED' | 'OFFLINE';
export type UserRole = 'CITIZEN' | 'RESPONDER' | 'ADMIN';
export type AccountStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
export type PredefinedMessageType = 'SOS' | 'ACK' | 'STATUS';
export type IncidentMessageChannel = 'ONLINE' | 'BLE';

// PSGC geo hierarchy lookups (GET /geo/provinces|municipalities|barangays|addresses)
export interface Province {
  id: number;
  code: string;
  name: string;
  created_at: string;
}

export interface Municipality {
  id: number;
  province_id: number;
  code: string;
  name: string;
  created_at: string;
}

export interface Barangay {
  id: number;
  municipality_id: number;
  code: string;
  name: string;
  created_at: string;
}

export interface Address {
  id: number;
  barangay_id: number;
  street_address: string;
  latitude: number;
  longitude: number;
  created_at: string;
}

export interface UserAccount {
  id: number | string;
  username: string;
  name: string;
  email: string;
  phone_number?: string;
  role: UserRole;
  account_status: AccountStatus;
  is_verified: boolean;
  national_id?: string;
  agency_id?: number;
  devices?: UserDevice[];
  emergency_contacts?: EmergencyContact[];
  created_at: string;
}

export interface EmergencyContact {
  id: number;
  user_id: number;
  contact_name: string;
  phone_number: string;
  relationship: string;
  is_primary: boolean;
  created_at?: string;
}

export interface UserDevice {
  id: number;
  user_id: number;
  device_hardware_id: string;
  device_name: string;
  platform: string;
  last_seen_at?: string;
  created_at?: string;
}

export interface AuditLog {
  id: number;
  actor_id?: number;
  action: string;
  entity_type: string;
  entity_id: number;
  old_value?: string;
  new_value?: string;
  created_at: string;
}

export interface IncidentStatusHistory {
  id: number;
  incident_id: number;
  status: IncidentStatus;
  changed_by?: number;
  latitude?: number;
  longitude?: number;
  notes?: string;
  created_at: string;
}

export interface IncidentDispatch {
  id: number;
  incident_id: number;
  unit_id: number;
  dispatched_by?: number;
  status: DispatchStatus;
  dispatched_at: string;
  accepted_at?: string;
  departed_at?: string;
  arrived_at?: string;
  completed_at?: string;
  unit_callsign?: string;
  agency_name?: string;
  responder_name?: string;
}

export interface IncidentMessage {
  id: number;
  incident_id: number;
  sender_user_id?: number;
  receiver_user_id?: number;
  predefined_message_id?: number;
  custom_message?: string;
  channel: IncidentMessageChannel;
  sent_at: string;
  received_at?: string;
  predefined_code?: string;
  message_en?: string;
  sender_name?: string;
  receiver_name?: string;
}


export interface TargetID {
  raw: string;
  region: string;
  province: string;
  muni: string;
  barangay: string;
  unit: string;
  uuid: string;
}

export interface TelemetryPacket {
  incident_uuid?: string;
  packet_uuid: string;
  packet_hash?: string;
  hmac_signature?: string;
  target_id: TargetID | { raw: string };
  incident_type: IncidentType;
  channel: ChannelType;
  delivery_path: DeliveryPath;
  latitude: number;
  longitude: number;
  altitude: number;
  gps_accuracy: number;
  hop_count: number;
  ttl: number;
  aes_payload?: string;
  sender_device_id: string;
  timestamp: string;
  received_at?: string;
}

export interface Incident {
  id: number;
  incident_uuid: string;
  packet_uuid?: string;
  packet_hash: string;
  target_id: TargetID;
  incident_type: IncidentType;
  channel: ChannelType;
  delivery_path: DeliveryPath;
  latitude: number;
  longitude: number;
  altitude: number;
  gps_accuracy: number;
  hop_count: number;
  ttl: number;
  sender_device_id: string;
  status: IncidentStatus;
  event_timestamp: string;
  received_at: string;
  created_at: string;
  updated_at: string;
}

export interface Agency {
  id: number;
  code: string;
  name: string;
  type: AgencyType;
  region_code: string;
  province_code: string;
  muni_code: string;
  barangay_code: string;
  contact_number: string;
  latitude: number;
  longitude: number;
  is_active: boolean;
  operating_hours: string;
  created_at?: string;
  updated_at?: string;
}

export interface ResponderUnit {
  id: number;
  agency_id: number;
  user_id?: number;
  target_id: string;
  unit_callsign: string;
  device_id: string;
  status: ResponderStatus | string;
  current_lat: number;
  current_lon: number;
  last_seen_at: string;
  responder_name?: string;
  responder_phone?: string;
  created_at?: string;
  updated_at?: string;
}

export interface RescueUnitMember {
  id: number;
  rescue_unit_id: number;
  responder_id: number;
  is_leader: boolean;
  assigned_at: string;
  removed_at?: string;
}

export interface EmergencyType {
  id: number;
  code: string;
  name: string;
  description?: string;
  default_priority: number;
  target_agency_type?: AgencyType;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface PredefinedMessage {
  id: number;
  code: string;
  message_en: string;
  message_fil?: string;
  message_bikol?: string;
  message_type: PredefinedMessageType;
  emergency_type_id?: number;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface IngestResponse {
  status: 'accepted' | 'deduplicated';
  deduplicated: boolean;
  incident?: Incident;
  message?: string;
}

export interface IncidentListResponse {
  // Total rows matching the filter across ALL pages — this is what
  // incident_handler.go's List() calls "total". Distinct from `count`,
  // which is just len(incidents) for the current page.
  total: number;
  count: number;
  limit: number;
  offset: number;
  incidents: Incident[];
}

export interface SyncBootstrap {
  user: UserAccount;
  agencies: Agency[];
  emergency_types: EmergencyType[];
  predefined_messages: PredefinedMessage[];
  assigned_dispatches?: IncidentDispatch[];
  active_incidents: Incident[];
}

export interface SyncPushRequest {
  telemetry_packets?: TelemetryPacket[];
  mesh_events?: Array<{
    packet_uuid: string;
    packet_type?: 'SOS' | 'ACK' | 'STATUS';
    event_type: 'CREATED' | 'RECEIVED' | 'RELAYED' | 'TARGET_REACHED' | 'UPLINKED' | 'DUPLICATE_DROPPED';
    device_identifier?: string;
    latitude?: number;
    longitude?: number;
    recorded_at?: string;
    synced_at?: string;
  }>;
}

export interface SyncPushResult {
  status: 'accepted' | 'partial';
  telemetry_accepted: number;
  telemetry_duplicate: number;
  mesh_events_recorded: number;
  errors?: string[];
}

export interface SyncChanges {
  since: string;
  server_time: string;
  incidents: Incident[];
  agencies: Agency[];
  emergency_types: EmergencyType[];
  predefined_messages: PredefinedMessage[];
}

export interface IncidentFilter {
  type?: string;
  status?: string;
  region?: string;
  limit?: number;
  offset?: number;
  since?: string;
}

export type NotificationType = 'NEW_INCIDENT' | 'INCIDENT_STATUS_UPDATED' | 'DISPATCH_ASSIGNED' | 'RESPONDER_LOCATION_UPDATED' | string;

export interface Notification {
  id: number;
  user_id: number;
  incident_id?: number;
  type: NotificationType;
  payload?: string;
  is_read: boolean;
  sent_at: string;
  read_at?: string;
}

export interface IncidentReport {
  id: number;
  report_uuid: string;
  transport_token: string;
  incident_id?: number;
  reporter_user_id?: number;
  reporter_device_id?: string | null;
  gateway_device_id?: string | null;
  submission_type: string;
  subject_relation: string;
  emergency_type: string;
  subtype?: string;
  description?: string | null;
  people_affected?: number | null;
  immediate_danger: boolean;
  location_source: string;
  selected_barangay_code?: string | null;
  latitude?: number;
  longitude?: number;
  gps_accuracy_meters?: number;
  observed_at: string;
  origin_channel: string;
  detail_state: 'MINIMAL' | 'FULL';
  created_at: string;
}
