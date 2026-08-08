import type {
  Agency,
  IncidentFilter,
  IncidentListResponse,
  IncidentStatus,
  DispatchStatus,
  AuditLog,
  EmergencyContact,
  IngestResponse,
  IncidentDispatch,
  IncidentMessage,
  IncidentStatusHistory,
  IncidentReport,
  EmergencyType,
  PredefinedMessage,
  RescueUnitMember,
  ResponderUnit,
  SyncBootstrap,
  SyncChanges,
  SyncPushRequest,
  SyncPushResult,
  TelemetryPacket,
  UserAccount,
  UserDevice,
} from '@/types/api';


export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';


async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('agap_auth_token');
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
    ...options,
  });

  if (!res.ok) {
    let errorMsg = `HTTP Error ${res.status}: ${res.statusText}`;
    try {
      const data = await res.json();
      if (data.error) errorMsg = data.error;
    } catch {
      // ignore
    }
    throw new Error(errorMsg);
  }

  if (res.status === 204) {
    return {} as T;
  }

  return res.json() as Promise<T>;
}

export const apiService = {
  async login(username: string, password: string): Promise<{ token: string; refresh_token?: string; expires_at: string; user: UserAccount }> {
    return request<{ token: string; refresh_token?: string; expires_at: string; user: UserAccount }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  },

  // System Health
  async getHealth(): Promise<{ status: string; service: string }> {
    const res = await fetch(`${API_BASE_URL.replace('/api/v1', '')}/health`);
    if (!res.ok) throw new Error('System unhealthy');
    return res.json();
  },

  // Telemetry Uplink
  async uplinkTelemetry(packet: TelemetryPacket): Promise<IngestResponse> {
    return request<IngestResponse>('/telemetry/uplink', {
      method: 'POST',
      body: JSON.stringify(packet),
    });
  },

  // Incidents
  async getIncidents(filter: IncidentFilter = {}): Promise<IncidentListResponse> {
    const params = new URLSearchParams();
    if (filter.type) params.append('type', filter.type);
    if (filter.status) params.append('status', filter.status);
    if (filter.region) params.append('region', filter.region);
    if (filter.limit) params.append('limit', filter.limit.toString());
    if (filter.offset) params.append('offset', filter.offset.toString());
    if (filter.since) params.append('since', filter.since);

    const queryString = params.toString();
    const path = `/incidents${queryString ? `?${queryString}` : ''}`;
    return request<IncidentListResponse>(path);
  },

  async updateIncidentStatus(id: number, status: IncidentStatus): Promise<{ status: string; incident_id: number; new_status: IncidentStatus }> {
    return request<{ status: string; incident_id: number; new_status: IncidentStatus }>(`/incidents/${id}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    });
  },

  async getIncidentStatusHistory(id: number): Promise<IncidentStatusHistory[]> {
    return request<IncidentStatusHistory[]>(`/incidents/${id}/status-history`);
  },

  async getIncidentDispatches(id: number): Promise<IncidentDispatch[]> {
    return request<IncidentDispatch[]>(`/incidents/${id}/dispatches`);
  },

  async getIncidentMessages(id: number): Promise<IncidentMessage[]> {
    return request<IncidentMessage[]>(`/incidents/${id}/messages`);
  },

  async getIncidentReports(id: number): Promise<{ success: boolean; reports: IncidentReport[] }> {
    return request<{ success: boolean; reports: IncidentReport[] }>(`/incidents/${id}/reports`);
  },

  async createIncidentMessage(
    id: number,
    message: {
      receiver_user_id?: number;
      predefined_message_id?: number;
      custom_message?: string;
      channel?: IncidentMessage['channel'];
    }
  ): Promise<IncidentMessage> {
    return request<IncidentMessage>(`/incidents/${id}/messages`, {
      method: 'POST',
      body: JSON.stringify(message),
    });
  },

  async updateDispatchStatus(incidentId: number, dispatchId: number, status: DispatchStatus): Promise<{ status: string; dispatch_id: number; new_status: DispatchStatus }> {
    return request<{ status: string; dispatch_id: number; new_status: DispatchStatus }>(`/incidents/${incidentId}/dispatches/${dispatchId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    });
  },

  // Agencies
  async getAgencies(): Promise<Agency[]> {
    return request<Agency[]>('/agencies');
  },

  async getAgencyById(id: number): Promise<Agency> {
    return request<Agency>(`/agencies/${id}`);
  },

  async createAgency(agency: Partial<Agency>): Promise<Agency> {
    return request<Agency>('/agencies', {
      method: 'POST',
      body: JSON.stringify(agency),
    });
  },

  async updateAgency(id: number, agency: Partial<Agency>): Promise<Agency> {
    return request<Agency>(`/agencies/${id}`, {
      method: 'PUT',
      body: JSON.stringify(agency),
    });
  },

  async deleteAgency(id: number): Promise<void> {
    await request<void>(`/agencies/${id}`, { method: 'DELETE' });
  },

  // Responders
  async getResponders(agencyId?: number): Promise<ResponderUnit[]> {
    const query = agencyId ? `?agency_id=${agencyId}` : '';
    return request<ResponderUnit[]>(`/rescue-units${query}`);
  },

  async getResponderById(id: number): Promise<ResponderUnit> {
    return request<ResponderUnit>(`/rescue-units/${id}`);
  },

  async createResponder(responder: Partial<ResponderUnit>): Promise<ResponderUnit> {
    return request<ResponderUnit>('/rescue-units', {
      method: 'POST',
      body: JSON.stringify(responder),
    });
  },

  async updateResponder(id: number, responder: Partial<ResponderUnit>): Promise<ResponderUnit> {
    return request<ResponderUnit>(`/rescue-units/${id}`, {
      method: 'PUT',
      body: JSON.stringify(responder),
    });
  },

  async updateResponderLocation(id: number, latitude: number, longitude: number, status: string): Promise<{ status: string; responder_id: number }> {
    return request<{ status: string; responder_id: number }>(`/rescue-units/${id}/location`, {
      method: 'PUT',
      body: JSON.stringify({ latitude, longitude, status }),
    });
  },

  async deleteResponder(id: number): Promise<void> {
    await request<void>(`/rescue-units/${id}`, { method: 'DELETE' });
  },

  async getRescueUnitMembers(id: number): Promise<RescueUnitMember[]> {
    return request<RescueUnitMember[]>(`/rescue-units/${id}/members`);
  },

  async getRescueUnitDispatches(id: number): Promise<IncidentDispatch[]> {
    return request<IncidentDispatch[]>(`/rescue-units/${id}/dispatches`);
  },

  async addRescueUnitMember(id: number, responderId: number, isLeader = false): Promise<RescueUnitMember> {
    return request<RescueUnitMember>(`/admin/rescue-units/${id}/members`, {
      method: 'POST',
      body: JSON.stringify({ responder_id: responderId, is_leader: isLeader }),
    });
  },

  async removeRescueUnitMember(id: number, responderId: number): Promise<void> {
    await request<void>(`/admin/rescue-units/${id}/members/${responderId}`, { method: 'DELETE' });
  },

  // Emergency Types
  async getEmergencyTypes(includeInactive = false): Promise<EmergencyType[]> {
    const query = includeInactive ? '?include_inactive=true' : '';
    return request<EmergencyType[]>(`/emergency-types${query}`);
  },

  async createEmergencyType(emergencyType: Partial<EmergencyType>): Promise<EmergencyType> {
    return request<EmergencyType>('/admin/emergency-types', {
      method: 'POST',
      body: JSON.stringify(emergencyType),
    });
  },

  async updateEmergencyType(id: number, emergencyType: Partial<EmergencyType>): Promise<EmergencyType> {
    return request<EmergencyType>(`/admin/emergency-types/${id}`, {
      method: 'PUT',
      body: JSON.stringify(emergencyType),
    });
  },

  // Users
  async getUsers(role?: string): Promise<UserAccount[]> {
    const query = role ? `?role=${role}` : '';
    return request<UserAccount[]>(`/users${query}`);
  },

  async updateUserAccountStatus(id: number | string, status: UserAccount['account_status'], remarks = ''): Promise<void> {
    await request(`/admin/users/${id}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status, remarks }),
    });
  },

  async getUserDevices(): Promise<UserDevice[]> {
    return request<UserDevice[]>('/users/me/devices');
  },

  async addUserDevice(deviceHardwareId: string, deviceName = '', platform = ''): Promise<UserDevice> {
    return request<UserDevice>('/users/me/devices', {
      method: 'POST',
      body: JSON.stringify({
        device_hardware_id: deviceHardwareId,
        device_name: deviceName,
        platform,
      }),
    });
  },

  async deleteUserDevice(deviceId: number | string): Promise<void> {
    await request<void>(`/users/me/devices/${deviceId}`, { method: 'DELETE' });
  },

  async getEmergencyContacts(): Promise<EmergencyContact[]> {
    return request<EmergencyContact[]>('/users/me/contacts');
  },

  async addEmergencyContact(contact: Partial<EmergencyContact>): Promise<EmergencyContact> {
    return request<EmergencyContact>('/users/me/contacts', {
      method: 'POST',
      body: JSON.stringify(contact),
    });
  },

  async updateEmergencyContact(id: number | string, contact: Partial<EmergencyContact>): Promise<EmergencyContact> {
    return request<EmergencyContact>(`/users/me/contacts/${id}`, {
      method: 'PUT',
      body: JSON.stringify(contact),
    });
  },

  async setPrimaryEmergencyContact(id: number | string): Promise<EmergencyContact> {
    return request<EmergencyContact>(`/users/me/contacts/${id}/primary`, {
      method: 'PUT',
    });
  },

  async deleteEmergencyContact(id: number | string): Promise<void> {
    await request<void>(`/users/me/contacts/${id}`, { method: 'DELETE' });
  },

  async getAuditLogs(
    entityType?: string,
    entityId?: number | string,
    limit = 20,
    action?: string,
    dateFrom?: string,
    dateTo?: string
  ): Promise<AuditLog[]> {
    const params = new URLSearchParams({ limit: String(limit) });
    if (entityType) params.set('entity_type', entityType);
    if (entityId !== undefined && entityId !== null && entityId !== '') params.set('entity_id', String(entityId));
    if (action) params.set('action', action);
    if (dateFrom) params.set('date_from', `${dateFrom}T00:00:00Z`);
    if (dateTo) params.set('date_to', `${dateTo}T23:59:59Z`);
    return request<AuditLog[]>(`/admin/audit-logs?${params.toString()}`);
  },

  // Predefined BLE message catalog
  async getMessageCatalog(includeInactive = false): Promise<PredefinedMessage[]> {
    const query = includeInactive ? '?include_inactive=true' : '';
    return request<PredefinedMessage[]>(`/messages/catalog${query}`);
  },

  async createMessageCatalogEntry(message: Partial<PredefinedMessage>): Promise<PredefinedMessage> {
    return request<PredefinedMessage>('/admin/messages/catalog', {
      method: 'POST',
      body: JSON.stringify(message),
    });
  },

  async updateMessageCatalogEntry(id: number, message: Partial<PredefinedMessage>): Promise<PredefinedMessage> {
    return request<PredefinedMessage>(`/admin/messages/catalog/${id}`, {
      method: 'PUT',
      body: JSON.stringify(message),
    });
  },

  async getSyncBootstrap(): Promise<SyncBootstrap> {
    return request<SyncBootstrap>('/sync/bootstrap');
  },

  async pushSync(requestBody: SyncPushRequest): Promise<SyncPushResult> {
    return request<SyncPushResult>('/sync/push', {
      method: 'POST',
      body: JSON.stringify(requestBody),
    });
  },

  async getSyncChanges(since: string): Promise<SyncChanges> {
    const params = new URLSearchParams({ since });
    return request<SyncChanges>(`/sync/changes?${params.toString()}`);
  },
  async createDispatch(incidentId: number, unitId: number): Promise<IncidentDispatch> {
    return request<IncidentDispatch>(`/incidents/${incidentId}/dispatch`, {
      method: 'POST',
      body: JSON.stringify({ unit_id: unitId }),
    });
  },
};


