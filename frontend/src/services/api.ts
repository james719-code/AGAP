import type {
  AccountStatus,
  Agency,
  Address,
  Barangay,
  DatabaseTable,
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
  Municipality,
  Notification,
  PredefinedMessage,
  Province,
  RescueUnitMember,
  ResponderUnit,
  SyncBootstrap,
  SyncChanges,
  SyncPushRequest,
  SyncPushResult,
  SystemInfoResponse,
  TableDataResponse,
  TelemetryPacket,
  UserAccount,
  UserDevice,
  UserRole,
} from '@/types/api';


export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// Endpoints that must never trigger a refresh-and-retry cycle — refreshing
// on a failed /auth/refresh or /auth/login would either loop forever or
// mask the real "wrong credentials" error.
const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/logout'];

// Called once we know the session is truly dead (refresh failed, or there
// was no refresh token to try). AuthContext registers this on mount so
// React state gets cleared in the same tick as localStorage, instead of
// the app staying "visually logged in" while every request 401s.
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

function clearSession() {
  localStorage.removeItem('agap_auth_token');
  localStorage.removeItem('agap_refresh_token');
  localStorage.removeItem('agap_user');
}

// Multiple requests can 401 around the same time (e.g. several hooks
// fetching on mount). Share a single in-flight refresh so we don't fire
// N parallel POST /auth/refresh calls that race each other.
let refreshPromise: Promise<string | null> | null = null;

async function refreshAuthToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem('agap_refresh_token');
  if (!refreshToken) return null;

  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refresh_token: refreshToken }),
        });
        if (!res.ok) return null;
        const data = (await res.json()) as { token: string; refresh_token?: string; expires_at: string };
        localStorage.setItem('agap_auth_token', data.token);
        if (data.refresh_token) {
          localStorage.setItem('agap_refresh_token', data.refresh_token);
        }
        return data.token;
      } catch {
        return null;
      } finally {
        refreshPromise = null;
      }
    })();
  }

  return refreshPromise;
}

async function request<T>(path: string, options?: RequestInit, isRetry = false): Promise<T> {
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
    if (res.status === 401 && !isRetry && !AUTH_ENDPOINTS.includes(path)) {
      const newToken = await refreshAuthToken();
      if (newToken) {
        // Retry the original request exactly once with the fresh token.
        return request<T>(path, options, true);
      }
      // No refresh token, or the refresh itself failed — the session is
      // dead. Clear storage AND notify React state synchronously so a
      // protected route can't stay rendered while every call fails.
      clearSession();
      onUnauthorized?.();
    } else if (res.status === 401) {
      // Retry already failed, or this was an auth endpoint itself.
      clearSession();
      onUnauthorized?.();
    }
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

  async logout(): Promise<void> {
    const refreshToken = localStorage.getItem('agap_refresh_token');
    // Best-effort: revoke the refresh token server-side. Callers should
    // clear local session state regardless of whether this succeeds, so
    // a flaky network never blocks the user from logging out locally.
    await request<void>('/auth/logout', {
      method: 'POST',
      body: JSON.stringify(refreshToken ? { refresh_token: refreshToken } : {}),
    });
  },

  async getCurrentUser(): Promise<UserAccount> {
    return request<UserAccount>('/users/me');
  },

  // Admin user roster (GET /users). The backend filters by role when provided.
  async getUsers(role?: UserRole): Promise<UserAccount[]> {
    const query = role ? `?role=${encodeURIComponent(role)}` : '';
    return request<UserAccount[]>(`/users${query}`);
  },

  // System Health & Info
  async getHealth(): Promise<{ status: string; service: string }> {
    const res = await fetch(`${API_BASE_URL.replace('/api/v1', '')}/health`);
    if (!res.ok) throw new Error('System unhealthy');
    return res.json();
  },

  async getSystemInfo(): Promise<SystemInfoResponse> {
    return request<SystemInfoResponse>('/info');
  },

  async getDatabaseTables(): Promise<DatabaseTable[]> {
    return request<DatabaseTable[]>('/system/db/tables');
  },

  async getTableData(tableName: string): Promise<TableDataResponse> {
    return request<TableDataResponse>(`/system/db/tables/${encodeURIComponent(tableName)}`);
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

  // Bulk variant of getIncidentStatusHistory — fetches history for many
  // incidents in one request instead of one call per incident. Backend caps
  // this at 200 IDs per call; callers with more incidents should chunk.
  async getIncidentStatusHistoryBulk(ids: number[]): Promise<Record<number, IncidentStatusHistory[]>> {
    if (ids.length === 0) return {};
    const params = new URLSearchParams({ incident_ids: ids.join(',') });
    return request<Record<number, IncidentStatusHistory[]>>(`/incidents/status-history?${params.toString()}`);
  },

  async getIncidentDispatches(id: number): Promise<IncidentDispatch[]> {
    return request<IncidentDispatch[]>(`/incidents/${id}/dispatches`);
  },

  // Bulk variant of getIncidentDispatches — see getIncidentStatusHistoryBulk.
  async getIncidentDispatchesBulk(ids: number[]): Promise<Record<number, IncidentDispatch[]>> {
    if (ids.length === 0) return {};
    const params = new URLSearchParams({ incident_ids: ids.join(',') });
    return request<Record<number, IncidentDispatch[]>>(`/incidents/dispatches?${params.toString()}`);
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

  async deleteEmergencyType(id: number): Promise<void> {
    await request<void>(`/admin/emergency-types/${id}`, { method: 'DELETE' });
  },

  // Users & Profile
  async updateMyProfile(profile: Partial<UserAccount>): Promise<UserAccount> {
    return request<UserAccount>('/users/me', {
      method: 'PUT',
      body: JSON.stringify(profile),
    });
  },

  async verifyUser(id: number | string): Promise<{ message: string; user_id: number }> {
    return request<{ message: string; user_id: number }>(`/admin/users/${id}/verify`, {
      method: 'PUT',
    });
  },

  async updateUserStatus(id: number | string, status: AccountStatus, remarks?: string): Promise<{ message: string; user_id: number; status: AccountStatus }> {
    return request<{ message: string; user_id: number; status: AccountStatus }>(`/admin/users/${id}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status, remarks }),
    });
  },

  async updateUser(id: number | string, user: Partial<UserAccount>): Promise<UserAccount> {
    return request<UserAccount>(`/admin/users/${id}`, {
      method: 'PUT',
      body: JSON.stringify(user),
    });
  },

  async deleteUser(id: number | string): Promise<void> {
    await request<void>(`/admin/users/${id}`, { method: 'DELETE' });
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

  // Geo hierarchy — public PSGC lookups. Optional parent filters mirror the
  // backend query params (province_id / municipality_id / barangay_id).
  async getProvinces(): Promise<Province[]> {
    return request<Province[]>('/geo/provinces');
  },

  async getMunicipalities(provinceId?: number | string): Promise<Municipality[]> {
    const query = provinceId ? `?province_id=${provinceId}` : '';
    return request<Municipality[]>(`/geo/municipalities${query}`);
  },

  async getBarangays(municipalityId?: number | string): Promise<Barangay[]> {
    const query = municipalityId ? `?municipality_id=${municipalityId}` : '';
    return request<Barangay[]>(`/geo/barangays${query}`);
  },

  async getAddresses(barangayId?: number | string): Promise<Address[]> {
    const query = barangayId ? `?barangay_id=${barangayId}` : '';
    return request<Address[]>(`/geo/addresses${query}`);
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

  async deleteMessageCatalogEntry(id: number): Promise<void> {
    await request<void>(`/admin/messages/catalog/${id}`, { method: 'DELETE' });
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

  // Notifications
  async getNotifications(unreadOnly = false): Promise<Notification[]> {
    const query = unreadOnly ? '?unread=true' : '';
    return request<Notification[]>(`/notifications${query}`);
  },

  async markNotificationRead(id: number): Promise<void> {
    await request<void>(`/notifications/${id}/read`, { method: 'PUT' });
  },
};