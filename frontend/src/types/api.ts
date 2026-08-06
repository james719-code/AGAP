export type IncidentType = 'CRIME' | 'MEDICAL' | 'ACCIDENT' | 'DISASTER';
export type ChannelType = 'ONLINE' | 'BLE_MESH';
export type DeliveryPath = 'DIRECT_UPLINK' | 'BLE_CLOUD_SHORTCUT';
export type IncidentStatus = 'ACTIVE' | 'DISPATCHED' | 'RESOLVED';
export type AgencyType = 'RESCUE' | 'DRRMO' | 'POLICE' | 'FIRE' | 'MEDICAL' | 'VOLUNTEER';
export type ResponderStatus = 'IDLE' | 'DISPATCHED' | 'OFFLINE';
export type UserRole = 'CITIZEN' | 'RESPONDER' | 'DISPATCHER' | 'ADMIN';

export interface UserAccount {
  id: number | string;
  username: string;
  name: string;
  email: string;
  phone_number?: string;
  role: UserRole;
  is_verified: boolean;
  national_id?: string;
  agency_id?: number;
  created_at: string;
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
  packet_id: string;
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
  packet_id: string;
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
  created_at?: string;
  updated_at?: string;
}

export interface ResponderUnit {
  id: number;
  agency_id: number;
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


export interface IngestResponse {
  status: 'accepted' | 'deduplicated';
  deduplicated: boolean;
  incident?: Incident;
  message?: string;
}

export interface IncidentListResponse {
  count: number;
  limit: number;
  offset: number;
  incidents: Incident[];
}

export interface IncidentFilter {
  type?: string;
  status?: string;
  region?: string;
  limit?: number;
  offset?: number;
  since?: string;
}
