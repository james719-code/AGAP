import type {
  Agency,
  IncidentFilter,
  IncidentListResponse,
  IncidentStatus,
  IngestResponse,
  ResponderUnit,
  TelemetryPacket,
} from '@/types/api';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
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

  if (res.status === 24) {
    return {} as T;
  }

  return res.json() as Promise<T>;
}

export const apiService = {
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
      method: 'PATCH',
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
    await fetch(`${API_BASE_URL}/agencies/${id}`, { method: 'DELETE' });
  },

  // Responders
  async getResponders(agencyId?: number): Promise<ResponderUnit[]> {
    const query = agencyId ? `?agency_id=${agencyId}` : '';
    return request<ResponderUnit[]>(`/responders${query}`);
  },

  async getResponderById(id: number): Promise<ResponderUnit> {
    return request<ResponderUnit>(`/responders/${id}`);
  },

  async createResponder(responder: Partial<ResponderUnit>): Promise<ResponderUnit> {
    return request<ResponderUnit>('/responders', {
      method: 'POST',
      body: JSON.stringify(responder),
    });
  },

  async updateResponder(id: number, responder: Partial<ResponderUnit>): Promise<ResponderUnit> {
    return request<ResponderUnit>(`/responders/${id}`, {
      method: 'PUT',
      body: JSON.stringify(responder),
    });
  },

  async updateResponderLocation(id: number, latitude: number, longitude: number, status: string): Promise<{ status: string; responder_id: number }> {
    return request<{ status: string; responder_id: number }>(`/responders/${id}/location`, {
      method: 'PATCH',
      body: JSON.stringify({ latitude, longitude, status }),
    });
  },

  async deleteResponder(id: number): Promise<void> {
    await fetch(`${API_BASE_URL}/responders/${id}`, { method: 'DELETE' });
  },
};
